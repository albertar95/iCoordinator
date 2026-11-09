#!/usr/bin/env python3
"""Return check (Orchestrator v6.8). Copied from the skill text."""
import re, subprocess, sys

KEYS = ["TASK", "STATUS", "SUMMARY", "VALIDATION", "CHECKS", "FLAGS", "QUESTIONS", "SUSPECT", "FILES"]

def arg(a, name, default=None):
    return a[a.index(name) + 1] if name in a else default

def brief_validation(text, task):
    if task:
        m = re.search(rf"=== TASK {re.escape(task)} ===(.*?)(?==== TASK |\Z)", text, re.S)
        if not m:
            return None
        text = m.group(1)
    cmds = re.findall(r"^VALIDATION:\s*(.+)$", text, re.M)
    return cmds[-1].strip() if cmds else None

def main(a):
    if len(a) < 2:
        print(__doc__); return 2
    ret = open(a[0], encoding="utf-8").read()
    brief = open(a[1], encoding="utf-8").read()
    cwd, tmo, task = arg(a, "--cwd", "."), int(arg(a, "--timeout", "300")), arg(a, "--task")
    fail = False
    missing = [k for k in KEYS if not re.search(rf"^\s*{k}:", ret, re.M)]
    if missing:
        fail = True
        print("FAIL keys missing (Type A): " + ", ".join(missing))
    else:
        print("PASS all nine keys present")
    st = re.search(r"^\s*STATUS:\s*(\w+)", ret, re.M)
    status = st.group(1) if st else "?"
    cmd = brief_validation(brief, task)
    if not cmd:
        print("FAIL no VALIDATION line in the brief"); return 1
    if cmd.upper().startswith("NOT RUN"):
        print(f"SKIP validation ({cmd}); verify findings instead (Review: review tasks)")
        return 1 if fail else 0
    try:
        p = subprocess.run(["bash", "-c", cmd], cwd=cwd, capture_output=True, text=True, timeout=tmo)
        out = (p.stdout + p.stderr).strip().splitlines()
        m = [l for l in out if re.match(r"exit=\d+", l.strip())]
        code = int(m[-1].strip()[5:]) if m else p.returncode
        tail = [l for l in out if not re.match(r"exit=\d+", l.strip())][-3:]
    except subprocess.TimeoutExpired:
        code, tail = 124, ["timeout"]
    for l in tail:
        print("  | " + l[:200])
    if code != 0:
        fail = True
        print(f"FAIL validation re-run exit={code}")
        if status == "DONE":
            print("FAIL return says DONE but the re-run fails (false pass, Type A)")
    else:
        print("PASS validation re-run exit=0")
    print("RESULT: FAIL" if fail else "RESULT: PASS")
    return 1 if fail else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
