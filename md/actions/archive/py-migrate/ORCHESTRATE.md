# Orchestrator Dispatch — py-migrate

> **Feature dir:** `md/actions/py-migrate/`
> **State machine:** `md/actions/py-migrate/AUDIT.md`
> **Executor prompt template:** one `PHASE_N.md` per phase
> **You are the orchestrator. You do not execute. You dispatch and review.**

---

## State Check

1. Open `md/actions/py-migrate/AUDIT.md`
2. Scan the Phase Execution Log table
3. Find the **first row** where `Status = 🔲 PENDING`
4. That is the current phase — note its phase number N

> If all rows are ✅ DONE → go to **Done** below.
> If any row is ⚠️ DEVIATION → go to **Deviation** below. Do not advance.

---

## Dispatch

1. Open `md/actions/py-migrate/PHASE_N.md` for the current phase
2. In `AUDIT.md`, mark that row `Status → ⏳ IN FLIGHT` **before dispatching**
3. Paste its **entire contents** into a **fresh agent session with no prior context**
4. Wait for the agent to return its structured Report block

---

## Review

Read the agent's Report block:

| Result | Action |
|--------|--------|
| `Status: PASS` | Fill `Verify Output` in AUDIT.md → mark row `✅ DONE` → dispatch next phase |
| `Status: DEVIATION` | Mark row `⚠️ DEVIATION` → **stop** → assess manually → do not advance |

> **Never advance past a DEVIATION without manual resolution.**
> **Never send the same agent Phase N+1. Always start a fresh session.**

---

## Deviation

1. Mark the row `⚠️ DEVIATION` in AUDIT.md
2. Read the deviation description from the agent's report
3. Assess: is the deviation benign (formatting) or material (wrong file, wrong logic)?
4. Benign → document it in `## Deviations` section of AUDIT.md → override to ✅ DONE → continue
5. Material → stop the run → fix the plan → re-dispatch from this phase

---

## Critical Constraint

> [!CAUTION]
> The `java/` directory is READ-ONLY. If any executor agent reports touching files inside `java/`, that is an immediate MATERIAL DEVIATION. Stop, revert with `git restore java/`, and investigate before continuing.

---

## Rollback

**Uncommitted changes:**
```bash
git restore python/   # revert all unstaged python/ changes
git restore md/       # revert all unstaged md/ changes
```

**Phase was committed and needs undoing:**
```bash
git revert HEAD --no-edit   # safe — adds revert commit
```

After rollback: reset the affected AUDIT.md row to `🔲 PENDING`, clear `Verify Output` and `Pass/Fail`, fix the plan, re-dispatch from a fresh session.

---

## Done

All rows `✅ DONE`:

1. Complete `## Sign-off` in AUDIT.md
2. Run archive per SKILL.md Step 5:
   ```bash
   git mv md/actions/py-migrate/ md/actions/archive/py-migrate/
   ```
3. Commit with `/commit` scope `chore(actions)`
