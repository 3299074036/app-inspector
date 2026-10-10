#!/usr/bin/env python3
"""Remove duplicate .class entries across classpath jars.

For each duplicated class, keep it in the highest-version jar and strip it
from the others (rewritten into <WORK>/patched/, classpath.txt updated).
This replaces the manual fixes for e.g. collection-ktx vs collection and
lifecycle-viewmodel-ktx vs lifecycle-viewmodel.
"""
import os, re, sys, zipfile, shutil
from collections import defaultdict

WORK = os.environ.get("MB_WORK", os.path.expanduser("~/manual-build"))
CP_FILE = os.path.join(WORK, "classpath.txt")
PATCHED = os.path.join(WORK, "patched")
os.makedirs(PATCHED, exist_ok=True)


def ver_key(v):
    parts = []
    for p in re.split(r"[.\-]", v or "0"):
        parts.append((0, int(p)) if p.isdigit() else (1, p))
    return parts


def jar_version(path):
    # group__artifact__version.jar  or  aar/group__artifact__version/classes.jar
    m = re.search(r"__([^/_]+?)(?:\.jar|/classes\.jar)$", path)
    if m:
        return m.group(1)
    m = re.search(r"-(\d[\d.]*)\.jar$", path)
    return m.group(1) if m else "0"


def main():
    jars = [l.strip() for l in open(CP_FILE) if l.strip()]
    class_to_jars = defaultdict(list)
    jar_classes = {}
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                names = [n for n in z.namelist()
                         if n.endswith(".class") and not n.startswith("META-INF")]
        except Exception as e:
            print(f"WARN cannot read {j}: {e}")
            names = []
        jar_classes[j] = names
        for n in names:
            class_to_jars[n].append(j)

    dups = {c: js for c, js in class_to_jars.items() if len(js) > 1}
    print(f"total classes: {len(class_to_jars)}, duplicated: {len(dups)}")
    if not dups:
        return

    # winner per class: highest version; tie -> first in classpath order
    strip_from = defaultdict(set)  # jar -> set of classes to remove
    for cls, js in dups.items():
        best = max(js, key=lambda j: (ver_key(jar_version(j)),
                                      -jars.index(j)))
        for j in js:
            if j != best:
                strip_from[j].add(cls)

    new_classpath = []
    for j in jars:
        if j not in strip_from:
            new_classpath.append(j)
            continue
        drop = strip_from[j]
        base = os.path.basename(j)
        if j.endswith("/classes.jar"):
            # AAR classes.jar -> name it after the AAR dir
            aar_name = os.path.basename(os.path.dirname(j))
            base = aar_name + "-classes-patched.jar"
        else:
            base = base.replace(".jar", "-patched.jar")
        dest = os.path.join(PATCHED, base)
        with zipfile.ZipFile(j) as zin:
            with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    if item.filename in drop:
                        continue
                    zout.writestr(item, zin.read(item.filename))
        print(f"  patched {os.path.basename(j)}: removed {len(drop)} duplicate classes -> {base}")
        new_classpath.append(dest)

    with open(CP_FILE, "w") as f:
        for j in new_classpath:
            f.write(j + "\n")
    print("classpath.txt updated")

    # final check
    class_to_jars2 = defaultdict(set)
    for j in new_classpath:
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                if n.endswith(".class") and not n.startswith("META-INF"):
                    class_to_jars2[n].add(j)
    dups2 = {c: js for c, js in class_to_jars2.items() if len(js) > 1}
    print(f"after patch: duplicated classes = {len(dups2)}")
    if dups2:
        sys.exit(1)


if __name__ == "__main__":
    main()
