#!/usr/bin/env python3
"""Resolve + download Maven artifacts (aar/jar) without Gradle.
Fetches POMs, follows compile/runtime deps, downloads artifacts via curl-able HTTPS."""
import os, re, sys, socket, urllib.request, xml.etree.ElementTree as ET

socket.setdefaulttimeout(25)

GOOGLE = "https://maven.google.com"
CENTRAL = "https://repo1.maven.org/maven2"
WORK = os.environ.get("MB_WORK", os.path.expanduser("~/manual-build"))
OUT = os.path.join(WORK, "deps")
os.makedirs(OUT, exist_ok=True)

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "manual-build/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def gav_path(g, a, v, ext):
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"


def try_download(g, a, v, ext):
    dest = os.path.join(OUT, f"{g}__{a}__{v}.{ext}")
    if os.path.exists(dest) and os.path.getsize(dest) > 1024:
        return dest
    for repo in (GOOGLE, CENTRAL):
        url = f"{repo}/{gav_path(g, a, v, ext)}"
        try:
            data = fetch(url)
            if data[:200].lower().lstrip().startswith(b"<html"):
                continue
            with open(dest, "wb") as f:
                f.write(data)
            print(f"  DL {g}:{a}:{v} ({ext}) {len(data)//1024}KB", flush=True)
            return dest
        except Exception:
            continue
    return None


_pom_cache = {}


def get_pom(g, a, v):
    key = (g, a, v)
    if key in _pom_cache:
        return _pom_cache[key]
    data = None
    for repo in (GOOGLE, CENTRAL):
        try:
            data = fetch(f"{repo}/{gav_path(g, a, v, 'pom')}")
            break
        except Exception:
            continue
    _pom_cache[key] = data
    return data


def text(el, tag):
    c = el.find(f"m:{tag}", NS)
    return c.text.strip() if c is not None and c.text else ""


def parse_props(root):
    props = {}
    p = root.find("m:properties", NS)
    if p is not None:
        for c in p:
            props[c.tag.split("}")[-1]] = (c.text or "").strip()
    return props


def subst(s, props, proj_version):
    def rep(m):
        k = m.group(1)
        if k in ("project.version", "pom.version"):
            return proj_version
        return props.get(k, m.group(0))
    return re.sub(r"\$\{([^}]+)\}", rep, s or "")


chosen = {}    # (g,a) -> v (最高版本胜出)
artifacts = {}   # (g,a,v) -> dict(packaging, ext, file)
managed = {}     # (g,a) -> v from BOM
skipped_nover = []
_pom_count = [0]


def ver_key(v):
    # 简单版本比较：数字部分优先
    parts = []
    for p in re.split(r"[.\-]", v):
        parts.append((0, int(p)) if p.isdigit() else (1, p))
    return parts


def load_bom(g, a, v):
    data = get_pom(g, a, v)
    if not data:
        print(f"ERROR: cannot fetch BOM {g}:{a}:{v}")
        sys.exit(1)
    root = ET.fromstring(data)
    props = parse_props(root)
    dm = root.find("m:dependencyManagement/m:dependencies", NS)
    n = 0
    if dm is not None:
        for d in dm.findall("m:dependency", NS):
            gg = subst(text(d, "groupId"), props, v)
            aa = subst(text(d, "artifactId"), props, v)
            vv = subst(text(d, "version"), props, v)
            if gg and aa and vv and "${" not in vv:
                managed[(gg, aa)] = vv
                n += 1
    print(f"BOM {g}:{a}:{v}: {n} managed versions")


def version_from_parents(g, a, v, dep_g, dep_a, depth=0):
    ck = (g, a, v, dep_g, dep_a)
    if ck in _parent_ver_cache:
        return _parent_ver_cache[ck]
    result = _version_from_parents(g, a, v, dep_g, dep_a, depth)
    _parent_ver_cache[ck] = result
    return result


def _version_from_parents(g, a, v, dep_g, dep_a, depth=0):
    if depth > 5:
        return None
    data = get_pom(g, a, v)
    if not data:
        return None
    root = ET.fromstring(data)
    props = parse_props(root)
    dm = root.find("m:dependencyManagement/m:dependencies", NS)
    if dm is not None:
        for d in dm.findall("m:dependency", NS):
            if text(d, "groupId") == dep_g and text(d, "artifactId") == dep_a:
                vv = subst(text(d, "version"), props, v)
                if vv and "${" not in vv:
                    return vv
    p = root.find("m:parent", NS)
    if p is not None:
        pg, pa, pv = text(p, "groupId"), text(p, "artifactId"), text(p, "version")
        if pg and pa and pv:
            return version_from_parents(pg, pa, pv, dep_g, dep_a, depth + 1)
    return None


def visit(g, a, v, stack=()):
    """贪心最高版本胜出：每个 (g,a) 只保留最高版本并遍历其依赖"""
    key = (g, a)
    if key in stack:
        return
    cur = chosen.get(key)
    if cur is not None and ver_key(v) <= ver_key(cur):
        return
    chosen[key] = v
    data = get_pom(g, a, v)
    _pom_count[0] += 1
    if _pom_count[0] % 25 == 0:
        print(f"  ... {_pom_count[0]} poms, {len(chosen)} artifacts", flush=True)
    if data is None:
        print(f"WARN no pom for {g}:{a}:{v}")
        return
    root = ET.fromstring(data)
    props = parse_props(root)
    deps = root.find("m:dependencies", NS)
    if deps is None:
        return
    for d in deps.findall("m:dependency", NS):
        scope = text(d, "scope") or "compile"
        if scope not in ("compile", "runtime"):
            continue
        if text(d, "optional") == "true":
            continue
        t = text(d, "type")
        if t and t not in ("jar", "aar"):
            continue
        dg = subst(text(d, "groupId"), props, v)
        da = subst(text(d, "artifactId"), props, v)
        dv = subst(text(d, "version"), props, v)
        if not dg or not da:
            continue
        if "${" in (dv or ""):
            dv = ""
        if dv.startswith("["):
            # 版本区间：[1.9.3] 取精确值，[1.0,2.0) 取下限
            m = re.match(r"\[\s*([^\],\s]+)", dv)
            dv = m.group(1) if m else ""
        if not dv:
            dv = managed.get((dg, da))
        if not dv:
            skipped_nover.append(f"{dg}:{da} (of {g}:{a}:{v})")
            continue
        visit(dg, da, dv, stack + (key,))


def download_chosen():
    """下载 chosen 中每个 (g,a) 的最高版本"""
    for (g, a), v in sorted(chosen.items()):
        data = get_pom(g, a, v)
        packaging = "jar"
        if data is not None:
            try:
                packaging = text(ET.fromstring(data), "packaging") or "jar"
            except Exception:
                pass
        if packaging == "pom":
            artifacts[(g, a, v)] = {"packaging": packaging, "ext": "pom", "file": None}
            continue
        ext = "aar" if packaging == "aar" else "jar"
        f = try_download(g, a, v, ext)
        if f is None and ext == "aar":
            f = try_download(g, a, v, "jar")
            if f:
                ext = "jar"
        artifacts[(g, a, v)] = {"packaging": packaging, "ext": ext, "file": f}
        if f is None:
            print(f"ERROR cannot download {g}:{a}:{v}")


ROOTS = [
    # 显式版本优先（先访问，避免被传递依赖 pin 到旧版本）
    ("androidx.activity", "activity-compose", "1.9.3"),
    ("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.6"),
    ("androidx.lifecycle", "lifecycle-runtime-ktx", "2.8.6"),
    ("androidx.core", "core-ktx", "1.15.0"),
    ("androidx.documentfile", "documentfile", "1.0.1"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.9.0"),
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21"),
    ("org.jetbrains", "annotations", "13.0"),
    # BOM 管理版本
    ("androidx.compose.ui", "ui", None),
    ("androidx.compose.foundation", "foundation", None),
    ("androidx.compose.foundation", "foundation-layout", None),
    ("androidx.compose.material3", "material3", None),
    ("androidx.compose.material", "material-icons-extended", None),
    ("androidx.compose.runtime", "runtime", None),
]

load_bom("androidx.compose", "compose-bom", "2024.10.00")
for g, a, v in ROOTS:
    if v is None:
        v = managed.get((g, a))
        if not v:
            print(f"ERROR no managed version for {g}:{a}")
            sys.exit(1)
    visit(g, a, v)

print(f"graph: {len(chosen)} artifacts")
download_chosen()

print(f"\nResolved {len(chosen)} artifacts, downloaded {sum(1 for x in artifacts.values() if x['file'])}")
missing = [(g, a, v) for (g, a, v), x in artifacts.items()
           if not x["file"] and x["packaging"] != "pom"]
if missing:
    print("MISSING:")
    for g, a, v in missing:
        print(f"  {g}:{a}:{v}")
    sys.exit(1)

with open(os.path.join(WORK, "artifacts.txt"), "w") as f:
    for (g, a, v), x in sorted(artifacts.items()):
        f.write(f"{g}\t{a}\t{v}\t{x['ext']}\t{x['file']}\n")
print(f"manifest written to {WORK}/artifacts.txt")
