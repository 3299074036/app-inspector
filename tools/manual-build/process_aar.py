#!/usr/bin/env python3
"""Unpack AARs: classes.jar for classpath, res/ for aapt2, check manifests for components."""
import os, zipfile, shutil

WORK = os.environ.get("MB_WORK", os.path.expanduser("~/manual-build"))
DEPS = os.path.join(WORK, "deps")
AAR = os.path.join(WORK, "aar")
os.makedirs(AAR, exist_ok=True)

# dedupe: prefer -android variant when both foo and foo-android exist
entries = []
with open(os.path.join(WORK, "artifacts.txt")) as f:
    for line in f:
        g, a, v, ext, path = line.rstrip("\n").split("\t")
        if ext not in ("aar", "jar"):
            continue
        if not os.path.exists(path) or os.path.getsize(path) < 1024:
            continue
        entries.append((g, a, v, ext, path))

by_base = {}
for g, a, v, ext, path in entries:
    base = a[:-8] if a.endswith("-android") else a
    by_base.setdefault((g, base), []).append((g, a, v, ext, path))

jars = []      # classes.jar paths for classpath
res_dirs = []  # res/ dirs for aapt2
manifest_hits = []

for (g, base), variants in sorted(by_base.items()):
    # prefer -android variant
    variants.sort(key=lambda x: (0 if x[1].endswith("-android") else 1))
    g, a, v, ext, path = variants[0]
    name = f"{g}__{a}__{v}"
    if ext == "jar":
        jars.append(path)
        continue
    dest = os.path.join(AAR, name)
    if not os.path.exists(os.path.join(dest, "classes.jar")):
        if os.path.exists(dest):
            shutil.rmtree(dest)
        os.makedirs(dest)
        with zipfile.ZipFile(path) as z:
            z.extractall(dest)
    cj = os.path.join(dest, "classes.jar")
    if os.path.exists(cj):
        jars.append(cj)
    else:
        print(f"WARN no classes.jar in {name}")
    res = os.path.join(dest, "res")
    if os.path.isdir(res) and os.listdir(res):
        res_dirs.append(res)
    mf = os.path.join(dest, "AndroidManifest.xml")
    if os.path.exists(mf):
        with open(mf, "rb") as f:
            content = f.read(4000)
        # binary XML; search for component tags as bytes
        for tag in (b"activity", b"service", b"provider", b"receiver"):
            if b"<" + tag in content:
                manifest_hits.append((name, tag.decode()))

with open(os.path.join(WORK, "classpath.txt"), "w") as f:
    for j in jars:
        f.write(j + "\n")
with open(os.path.join(WORK, "resdirs.txt"), "w") as f:
    for r in res_dirs:
        f.write(r + "\n")

print(f"classpath jars: {len(jars)}")
print(f"res dirs: {len(res_dirs)}")
for r in res_dirs:
    print("  RES", r)
if manifest_hits:
    print("manifest components found:")
    for n, t in manifest_hits:
        print(f"  {n}: <{t}>")
else:
    print("no components in AAR manifests")
