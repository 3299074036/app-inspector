#!/usr/bin/env python3
"""Generate R.java for library packages (non-namespaced build: library R classes
are aliases of the app resource table, using the same IDs from R.txt).

Usage: gen_r.py <packages-file>
  packages-file: one Java package per line (output of scan_r.py, minus 'android')
"""
import re, os, sys

WORK = os.environ.get("MB_WORK", os.path.expanduser("~/manual-build"))
rfile = os.path.join(WORK, 'R.txt')
outbase = os.path.join(WORK, 'gen-lib')
pkgs_file = sys.argv[1]

pkgs = [l.strip() for l in open(pkgs_file) if l.strip() and l.strip() != 'android']
if not pkgs:
    print("no library R packages needed")
    sys.exit(0)

# 解析 R.txt: {type: [(name, value)]}, value 可能是 "0x..." 或 "{...}" 或 "10"
syms = {}
for line in open(rfile):
    line = line.strip()
    m = re.match(r'^(int(\[\])?)\s+(\w+)\s+(\w+)\s+(.+)$', line)
    if not m:
        continue
    kind, arr, typ, name, val = m.groups()
    syms.setdefault(typ, []).append((name, val.strip()))

for pkg in pkgs:
    d = os.path.join(outbase, pkg.replace('.', '/'))
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'R.java'), 'w') as f:
        f.write('package %s;\npublic final class R {\n' % pkg)
        for typ in sorted(syms):
            f.write('  public static final class %s {\n' % typ)
            for name, val in syms[typ]:
                if val.startswith('{'):
                    f.write('    public static int[] %s=%s;\n' % (name, val))
                else:
                    f.write('    public static int %s=%s;\n' % (name, val))
            f.write('  }\n')
        f.write('}\n')
print('generated R.java for %d packages, %d symbols each'
      % (len(pkgs), sum(len(v) for v in syms.values())))
