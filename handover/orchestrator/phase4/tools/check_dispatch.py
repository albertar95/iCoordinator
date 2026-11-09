#!/usr/bin/env python3
"""Pre-dispatch check for one launch message (Orchestrator v6.8). Copied from the skill text."""
import re, sys

FIELDS = ["POLICY:", "Your job:", "INPUTS:", "OUTPUT:", "MAY MODIFY:", "ACCEPTANCE CRITERIA:", "VALIDATION:"]
STD = ["MUST STOP:", "CLEANUP:", "REPLY SIZE:", "RETURN FORMAT:", "scheduled tasks"]
PLACEHOLDER = re.compile(r"<[^<>\n]{1,60}>|\bTBD\b|\bTODO\b|\?\?\?|^\s*\d+\.\s*\.\.\.\s*$", re.M)

def field(part, name):
    m = re.search(rf"^{re.escape(name)}\s*(.*)$", part, re.M)
    return m.group(1).strip() if m else None

def files_of(v):
    if not v or v.lower().startswith("none"):
        return []
    return [x.strip().strip("`") for x in re.split(r"[,;]", v) if x.strip() and not x.strip().lower().startswith("none")]

def tasks(text):
    return re.findall(r"=== TASK (\S+) ===(.*?)(?==== TASK |\Z)", text, re.S)

def main(a):
    if not a:
        print(__doc__); return 2
    text = open(a[0], encoding="utf-8").read()
    others = a[a.index("--sprint") + 1:] if "--sprint" in a else []
    bad = 0
    def out(ok, msg):
        nonlocal bad
        bad += 0 if ok else 1
        print(("PASS " if ok else "FAIL ") + msg)
    n_std = text.count("=== STANDARD RULES ===")
    out(n_std == 1, f"standard rules block present once (found {n_std})")
    if n_std:
        std = text.split("=== STANDARD RULES ===", 1)[1].split("=== END STANDARD RULES ===", 1)[0]
        miss = [s for s in STD if s not in std]
        out(not miss, "standard block complete" + (": missing " + ", ".join(miss) if miss else ""))
    tl = tasks(text)
    out(bool(tl), f"{len(tl)} TASK part(s)")
    owners = {}
    for tid, part in tl:
        miss = [f for f in FIELDS if field(part, f) is None]
        out(not miss, f"{tid} fields" + (": missing " + ", ".join(miss) if miss else ""))
        ph = sorted(set(m.group(0) for m in PLACEHOLDER.finditer(part)))
        out(not ph, f"{tid} no placeholders" + (": " + ", ".join(ph[:5]) if ph else ""))
        v = field(part, "VALIDATION:") or ""
        ok_v = v.upper().startswith("NOT RUN") or re.search(r"echo[^;\n]*\$\?", v)
        out(bool(ok_v), f"{tid} VALIDATION prints its exit code")
        outs = [f for f in files_of(field(part, "OUTPUT:")) if not f.endswith("return.md")]
        mm = files_of(field(part, "MAY MODIFY:"))
        n = len(set(outs) | set(mm))
        out(n <= 3, f"{tid} changes {n} file(s) (limit 3)")
        for f in mm:
            owners.setdefault(f, []).append(tid)
    for path in others:
        for tid, part in tasks(open(path, encoding="utf-8").read()):
            for f in files_of(field(part, "MAY MODIFY:")):
                owners.setdefault(f, []).append(tid)
    shared = {f: t for f, t in owners.items() if len(set(t)) > 1}
    out(not shared, "no shared MAY MODIFY file" + ("".join(f"; {f}: {', '.join(t)}" for f, t in shared.items()) if shared else ""))
    print("RESULT: PASS" if not bad else f"RESULT: FAIL ({bad})")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
