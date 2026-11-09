#!/usr/bin/env python3
"""Static check for Android/Compose Kotlin files that cannot be compiled here.
Usage: uicheck.py FILE... [--require 'regex' ...]
Checks: balanced (), {}, [] outside strings/comments; no TODO( / !! ; every R.drawable.X exists in
project/app/src/main/res/drawable; every --require regex matches somewhere in the given files.
Prints PASS/FAIL lines; exit 0 = all pass."""
import re, sys, os
args = sys.argv[1:]
req = [args[i + 1] for i, a in enumerate(args) if a == '--require']
files = [a for i, a in enumerate(args) if a != '--require' and (i == 0 or args[i - 1] != '--require')]
draw = {f[:-4] for f in os.listdir('project/app/src/main/res/drawable')}
bad = 0
def out(ok, m):
    global bad
    bad += 0 if ok else 1
    print(('PASS ' if ok else 'FAIL ') + m)
def strip(src):
    """Remove comments, string and char literals (single pass state machine)."""
    o, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i); i = n if j < 0 else j; continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3); i = n if j < 0 else j + 3; o.append('""'); continue
        if c == '"':
            i += 1
            while i < n and src[i] != '"' and src[i] != '\n':
                i += 2 if src[i] == '\\' else 1
            i += 1; o.append('""'); continue
        m = re.match(r"'(\\.|[^'\\])'", src[i:i + 4])
        if c == "'" and m:
            i += len(m.group(0)); continue
        o.append(c); i += 1
    return ''.join(o)
alltext = ''
for f in files:
    if not os.path.exists(f):
        out(False, f'{f} missing'); continue
    src = open(f, encoding='utf-8').read(); alltext += src
    s = strip(src)
    for a, b in ['()', '{}', '[]']:
        out(s.count(a) == s.count(b), f'{f} balanced {a}{b} ({s.count(a)}/{s.count(b)})')
    out('TODO(' not in s, f'{f} no TODO(')
    out('!!' not in s, f'{f} no !!')
    if re.search(r'\b(val|var)\s+\w+\s+by\s+(?!lazy\b)', s) and 'androidx.compose.runtime' in src:
        out('import androidx.compose.runtime.getValue' in src or 'import androidx.compose.runtime.*' in src, f'{f} has getValue import for by-delegates')
    if re.search(r'\bvar\s+\w+\s+by\s+(?!lazy\b)', s) and 'androidx.compose.runtime' in src:
        out('import androidx.compose.runtime.setValue' in src or 'import androidx.compose.runtime.*' in src, f'{f} has setValue import for var by-delegates')
    missing = sorted({d for d in re.findall(r'R\.drawable\.(\w+)', s)} - draw)
    out(not missing, f'{f} drawables exist' + (': missing ' + ', '.join(missing) if missing else ''))
for r in req:
    out(re.search(r, alltext, re.M) is not None, f'require /{r}/')
print('RESULT: PASS' if not bad else f'RESULT: FAIL ({bad})')
sys.exit(1 if bad else 0)
