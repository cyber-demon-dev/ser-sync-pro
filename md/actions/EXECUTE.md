# Agent Execution Prompt — cdd-sync-pro

## Your Mission

Execute `AUDIT_FIXES_PLAN.md` — a 7-phase plan fixing five code-quality findings from Audit #1 of cdd-sync-pro: two duplicated-code concerns (C5-1: Serato path encoding block copied 3×; C5-2: `readInt` reimplemented inline), two silent error-swallowing concerns (C4-1, C4-2), and one duplicated NFC normalization pattern (C4-4). Your only job is to execute each phase exactly as written, verify each one, fill in the audit file after each phase, and commit the result.

**You do not interpret. You do not improve. You do not deviate.**

---

## Mandatory Pre-Flight Protocol

Run all five checks before touching any file. All five. Not four.

1. **Working directory**

   ```bash
   pwd
   ```

   Expected: `/Users/culprit/Git/cdd-sync-pro`
   STOP AND REPORT if any other directory is shown.

2. **Git status**

   ```bash
   git status
   ```

   Expected: `nothing to commit, working tree clean`
   STOP AND REPORT if any uncommitted changes are shown.

3. **Git branch**

   ```bash
   git branch --show-current
   ```

   Expected: `master`
   STOP AND REPORT if any other branch is shown.

4. **Plan file present**

   ```bash
   ls md/actions/AUDIT_FIXES_PLAN.md md/actions/AUDIT_FIXES_AUDIT.md
   ```

   Expected: both filenames listed with no error.
   STOP AND REPORT if either file is missing.

5. **Read both files in full**
   Read `md/actions/AUDIT_FIXES_PLAN.md` and `md/actions/AUDIT_FIXES_AUDIT.md` completely before touching any source file. You must know every phase, every file, every verify command before you begin.
   STOP AND REPORT if either file cannot be read.

---

## Execution Order

Execute in this order only. No interleaving.

1. `md/actions/AUDIT_FIXES_PLAN.md` — all 7 phases in sequence

---

## Strict Execution Rules

| Rule | Detail |
|------|--------|
| One file per phase | Never create or modify more than one file per phase. Phase 3 has two actions — **both are in the same file** (`cdd_sync_crate_fixer.java`). Phase 5 has two actions — **both are in the same file** (`cdd_sync_crate_scanner.java`). Phase 6 has two actions — **both are in the same file** (`session_fixer_core_logic.java`). |
| No interpretation | If the plan says replace lines X–Y with exact text, do exactly that. Do not improve it. |
| No additions | No extra comments, logging, imports, or features not in the plan. |
| No consolidation | Do not merge phases, even if they touch files you've already touched. |
| Verify after every phase | Run the Verify block. Confirm output matches. Then and only then proceed. |
| Stop on any failure | Verification fails → stop immediately → report exact terminal output → do not self-correct. |
| No fallback paths | A step fails → stop → report. There is no alternative approach. |
| Commit exactly as written | Phase 7 contains the exact `git commit -m "..."` message. Copy it character for character. |
| No bundling commits | There is one commit. Phase 7. Not earlier. |

---

## Verification Protocol (per phase)

1. Run the verify command listed in the phase's **Verify** block.
2. Confirm the output matches the expected result exactly.
3. Match → immediately fill in the corresponding audit row → proceed to the next phase.
4. No match → **STOP AND REPORT.** Do not proceed. Do not attempt to fix it yourself.

---

## Audit Fill-In (Mandatory)

After every phase's Verify block passes, **immediately** update the corresponding row in `md/actions/AUDIT_FIXES_AUDIT.md`:

1. Open `md/actions/AUDIT_FIXES_AUDIT.md`.
2. Fill in `Verify Output` with the actual command output (truncated to last 5 lines if long).
3. Set `Pass/Fail` to ✅ PASS or ⚠️ DEVIATION.
4. If ⚠️ DEVIATION: document it in `## Deviations` before touching the next phase.

**You do not proceed to Phase N+1 until Phase N's audit row is filled in. Not at the end. Not in bulk. One row. One phase. Right now.**

After Phase 7 (commit) passes:

- Paste `git log --oneline -3` into `## Final Commit Log`.
- Paste the last 8 lines of `ant test` output into `## Build Verification`.
- Check every sign-off box.

**You do not push until every audit sign-off box is checked. Not one box empty.**

---

## Build Verification

After each phase's Verify block, the expected output is:

```
BUILD SUCCESSFUL
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
```

(Exact format may vary — look for `BUILD SUCCESSFUL` and `Tests run: 26` with zero failures/errors.)

Any `BUILD FAILED`, compile error, or test failure = **STOP AND REPORT**.

---

## Final State Checklist

After Phase 7:

- [ ] `shared/src/cdd_sync_binary_utils.java` — `resolveSeratoPath()` method added
- [ ] `cdd-sync-pro/src/cdd_sync_crate.java` — `addTrack()` and `addTracksFiltered()` use `resolveSeratoPath()`
- [ ] `cdd-sync-pro/src/cdd_sync_crate_fixer.java` — encoding block replaced with `resolveSeratoPath()`, inline NFC normalization replaced with `getFilename()`, `Normalizer` import removed
- [ ] `cdd-sync-pro/src/cdd_sync_crate_scanner.java` — `readInt` inline replaced with `cdd_sync_binary_utils.readInt()`, `parseCrateFile` IOException logs to `cdd_sync_log.error()`
- [ ] `session-fixer/src/session_fixer_core_logic.java` — `deleteShortSessions` catch narrows to `cdd_sync_exception` + logs, both inline NFC normalizations replaced with `getFilename()`, `Normalizer` import removed
- [ ] `git log --oneline -1` shows: `refactor(audit): extract resolveSeratoPath, fix readInt dupe, log silent catches`
- [ ] `git status` is clean (nothing to commit)
- [ ] All 7 audit rows filled with actual verify output

---

## What You Are NOT Allowed To Do

- Rename any file
- Modify any file not listed in the plan
- Add tests, comments, logging, or features beyond what the plan specifies
- Combine or reorder phases
- Rephrase the commit message
- Push to origin before `ant test` passes with 26/26
- Leave any audit row blank at any point
- Self-correct on a failed verify step — stop and report instead
- Remove imports that are still used elsewhere in the file
- Revise the plan's approach even if you think you see a better way — you don't have context the planner had
