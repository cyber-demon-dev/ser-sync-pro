# Agent Execution Prompt — cdd-sync-pro Class Rename

## Your Mission

You will execute `RENAME_CLASSES_PLAN.md`. This plan renames every `ser_sync_*` Java source file, its public class declaration, and every reference to that class across the codebase. There are 30 phases. Your only job is to execute them in order, verify each one, fill in the audit after each phase, and commit at the end. You are not allowed to improvise, consolidate, or skip.

---

## Mandatory Pre-Flight Protocol

Run these checks before touching a single file. Each has an expected output. If the actual output does not match, **STOP AND REPORT** — do not proceed.

| # | Command | Expected Output | On failure |
|---|---------|-----------------|------------|
| 1 | `pwd` | `/Users/culprit/Git/cdd-sync-pro` | STOP AND REPORT |
| 2 | `git status` | `nothing to commit, working tree clean` | STOP AND REPORT |
| 3 | `git branch` | `* master` | STOP AND REPORT |
| 4 | `ls md/actions/RENAME_CLASSES_PLAN.md md/actions/RENAME_CLASSES_AUDIT.md` | Both filenames printed with no error | STOP AND REPORT |
| 5 | `ant test 2>&1 \| tail -5` | Last line: `BUILD SUCCESSFUL` | STOP AND REPORT |

**Read `md/actions/RENAME_CLASSES_PLAN.md` in full before executing Phase 1. Do not begin until you have read every line.**

---

## Execution Order

1. `md/actions/RENAME_CLASSES_PLAN.md` — all 30 phases, in order, one at a time

No other plan files. No interleaving. No exceptions.

---

## Strict Execution Rules

| Rule | Detail |
|------|--------|
| One file per phase | Never create or modify more than one file per phase |
| No interpretation | If the plan says run a specific command, run exactly that command |
| No additions | No extra comments, logging, imports, or features not in the plan |
| No consolidation | Do not merge phases or collapse steps, even if they seem redundant |
| Verify after every phase | Run the Verify block. Confirm output matches. Then and only then proceed. |
| Stop on any failure | Verification fails → stop immediately → report exact output → do not self-correct |
| No fallback paths | A step fails → stop → report. There is no alternative approach. |
| Don't run `ant test` mid-execution | The build is intentionally broken between phases. Run `ant test` only in Phase 29. |
| Commit exactly as written | `refactor(classes): rename ser_sync_* classes to cdd_sync_*` — copy it verbatim |
| No bundling commits | One commit at the end (Phase 30). Not per phase. |
| macOS sed syntax | Use `sed -i ''` (with empty string). NOT `sed -i` which is Linux syntax and will fail. |

---

## Verification Protocol (per phase)

1. Run the Verify command(s) from the phase's **Verify** section.
2. Confirm output matches expected exactly.
3. Match → fill in audit row → proceed to next phase.
4. No match → **STOP AND REPORT.** Do not proceed. Do not attempt to fix it yourself.

---

## Audit Fill-In (Mandatory)

After every phase completes and its Verify block passes, immediately update `md/actions/RENAME_CLASSES_AUDIT.md`:

1. Open the audit file.
2. Fill in `Verify Output` with the actual command output (if "no output" is expected: write `✓ no output`).
3. Set `Pass/Fail` to ✅ PASS or ⚠️ DEVIATION.
4. If ⚠️ DEVIATION: document it in `## Deviations` before touching the next phase.

**You do not proceed to the next phase until the audit row is filled. Not at the end. Not in bulk. One row per phase. Right now.**

After all phases and build verification complete:

- Paste the full `ant test` output into `## Build Verification`.
- Paste `git log --oneline -5` output into `## Final Commit Log`.
- Check every sign-off box.

**You do not push (Phase 30) until every audit sign-off box is checked.**

---

## Build Verification

After Phase 29:

```bash
ant clean && ant all && ant test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` as the final line. 26 tests reported as passing.

If the build fails: **STOP AND REPORT** the full `ant test` output. Do not push.

---

## Final State Checklist

Verify each item before pushing:

| Item | Expected |
|------|----------|
| `grep -r "ser_sync_" . --include="*.java" --include="*.xml" \| grep -v ".git"` | No output |
| `ls shared/src/cdd_sync_*.java` | 12 files |
| `ls cdd-sync-pro/src/cdd_sync_*.java` | 12 files |
| `ls test/cdd_sync_*_test.java` | 2 files |
| `grep "main.class" build.xml` | `value="cdd_sync_main"` |
| `grep "arg value" build.xml` | contains `cdd_sync_binary_utils_test` AND `cdd_sync_crate_test` |
| `git status` | `nothing to commit, working tree clean` |
| `git log --oneline -1` | `refactor(classes): rename ser_sync_* classes to cdd_sync_*` |
| `ls distr/cdd-sync-pro/cdd-sync-pro.jar` | file exists |

---

## What You Are NOT Allowed To Do

- **Rename `session_fixer_*` files** — they are out of scope. Period.
- **Run `ant test` between phases 1–28** — the build is broken mid-execution; this is expected.
- **Combine multiple phases into one step** — each phase is atomic. No shortcuts.
- **Rename `.md` files or update documentation** — the plan does not include doc updates. They will be handled separately.
- **Push before Phase 29 (`ant test`) passes** — no exceptions.
- **Push before every audit sign-off box is checked** — no exceptions.
- **Rephrase the commit message** — copy `refactor(classes): rename ser_sync_* classes to cdd_sync_*` exactly.
- **Add new tests or new imports** — nothing not in the plan.
- **Use Linux `sed -i` syntax** — this is macOS. Always `sed -i ''`.
- **Self-correct a failed Verify step** — stop and report. That is your only path.
- **Leave audit rows blank** — every row must be filled before the next phase begins.
- **Modify `build.xml` path/directory properties** — those were updated in a prior rename. Only class-name identifiers are in scope.
