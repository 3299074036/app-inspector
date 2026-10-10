import zipfile, re, struct, sys, os

WORK = os.environ.get("MB_WORK", os.path.expanduser("~/manual-build"))

def utf8_strings(data):
    # 粗糙但有效的 constant pool Utf8 提取：找 CONSTANT_Utf8 (tag=1) 条目
    out = set()
    if data[:4] != b'\xca\xfe\xba\xbe':
        return out
    n = struct.unpack('>H', data[8:10])[0]
    pos = 10
    for _ in range(1, n):
        if pos >= len(data): break
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack('>H', data[pos+1:pos+3])[0]
            try: out.add(data[pos+3:pos+3+ln].decode('utf-8', 'ignore'))
            except: pass
            pos += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            pos += 3
        elif tag in (15,):
            pos += 4
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
        else:
            break
    return out

pat = re.compile(r'^([a-z][a-zA-Z0-9_/]*)/R(\$[A-Za-z0-9_]+)?$')
pkgs = set()
jars = [l.strip() for l in open(os.path.join(WORK, 'classpath.txt')) if l.strip()]
for j in jars:
    try:
        z = zipfile.ZipFile(j)
    except Exception as e:
        print('ZIP FAIL', j, e); continue
    for n in z.namelist():
        if not n.endswith('.class'): continue
        try: data = z.read(n)
        except: continue
        for s in utf8_strings(data):
            m = pat.match(s)
            if m:
                pkgs.add(m.group(1).replace('/', '.'))
for p in sorted(pkgs):
    print(p)
