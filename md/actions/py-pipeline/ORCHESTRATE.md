# Orchestrator Dispatch — py-pipeline

> **Feature dir:** `md/actions/py-pipeline/`
> **State machine:** `md/actions/py-pipeline/AUDIT.md`
> **Executor prompt template:** one `PHASE_N.md` per phase (9 total)
> **You are the orchestrator. You do not execute. You dispatch and review.**

---

## State Check

1. Open `md/actions/py-pipeline/AUDIT.md`
2. Scan the Phase Execution Log table
3. Find the **first row** where `Status = 🔲 PENDING`
4. That is the current phase — note its phase number N

> If all rows are ✅ DONE → go to **Done** below.
> If any row is ⚠️ DEVIATION → go to **Deviation** below. Do not advance.

---

## Dispatch

1. Open `md/actions/py-pipeline/PHASE_N.md` for the current phase
2. In `AUDIT.md`, mark that row `Status → ⏳ IN FLIGHT` **before dispatching**
3. Paste its **entire contents** into a **fresh agent session with no prior context**
4. Wait for the agent to return its structured Report block

---

## Review

| Result | Action |
|--------|--------|
| `Status: PASS` | Fill `Verify Output` in AUDIT.md → mark row `✅ DONE` → dispatch next phase |
| `Status: DEVIATION` | Mark row `⚠️ DEVIATION` → **stop** → assess manually → do not advance |

> **Never advance past a DEVIATION without manual resolution.**
> **Never send the same agent Phase N+1. Always start a fresh session.**

---

## Critical Constraints

> [!CAUTION]
> The `java/` directory is READ-ONLY. Executor agents read Java files as specification **only**. If any agent modifies a file under `java/`, that is an immediate MATERIAL DEVIATION. Stop, run `git restore java/`, investigate before continuing.

> [!IMPORTANT]
> Do NOT modify `python/core/` — those files were completed in py-migrate. All new code lives in `python/config.py`, `python/sync/`, `python/main.py`, and `python/tests/test_pipeline.py`.

---

## Deviation

1. Mark row `⚠️ DEVIATION` in AUDIT.md
2. Assess: benign (formatting) or material (wrong file, wrong logic)?
3. Benign → document in `## Deviations` → override to ✅ DONE → continue
4. Material → stop → fix plan → re-dispatch from this phase in a fresh session

---

## Rollback

```bash
git restore python/   # revert unstaged python/ changes
git restore md/       # revert unstaged md/ changes
# or if committed:
git revert HEAD --no-edit
```

After rollback: reset AUDIT.md row to `🔲 PENDING`, fix plan, re-dispatch.

---

## Done

All rows `✅ DONE`:

1. Complete `## Sign-off` in AUDIT.md
2. Run archive:
   ```bash
   git mv md/actions/py-pipeline/ md/actions/archive/py-pipeline/
   ```
3. Commit with `/commit` scope `chore(actions)`
