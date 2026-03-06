# Agent Execution Prompt — ser-sync-pro Action Plans

## Your Mission

You are executing two pre-written implementation plans for the **ser-sync-pro** project. Both plans are complete, precise, and require zero interpretation. Your only job is to follow them exactly.

---

## Mandatory Pre-Flight Protocol

Before touching a single file, execute all of the following steps in order. Do not skip any.

### 1. Confirm working directory

```bash
pwd
```

Expected output must contain: `/ser-sync-pro`

If it does not, stop and report the actual path. **Do not proceed.**

### 2. Confirm clean git state

```bash
git status
```

Expected: `nothing to commit, working tree clean`

If there are uncommitted changes, stop and report. **Do not proceed.**

### 3. Confirm branch

```bash
git branch --show-current
```

Expected: `master`

If not on `master`, stop and report. **Do not proceed.**

### 4. Confirm both plan files exist

```bash
ls md/actions/CI_PIPELINE_PLAN.md md/actions/DRY_RUN_PLAN.md
```

Both files must be listed. If either is missing, stop and report. **Do not proceed.**

### 5. Read both plan files in full before making any changes

```bash
cat md/actions/CI_PIPELINE_PLAN.md
cat md/actions/DRY_RUN_PLAN.md
```

Do not begin execution until both files have been read completely.

---

## Execution Order

Execute the plans in this exact order:

1. **`CI_PIPELINE_PLAN.md`** — all phases, in sequence, verified after each phase
2. **`DRY_RUN_PLAN.md`** — all phases, in sequence, verified after each phase

Do not interleave phases between plans.

---

## Strict Execution Rules

These rules are absolute. No exceptions.

| Rule | Detail |
|------|--------|
| **One file per phase** | Never create or modify more than one file per phase as defined in the plan |
| **No interpretation** | If a plan says "insert after line X", do exactly that. Do not restructure or improve the code |
| **No additions** | Do not add comments, logging, imports, or features not specified in the plan |
| **No consolidation** | Do not merge phases or collapse steps, even if they touch the same file |
| **Verify after every phase** | Run the verification check specified at the end of each phase before moving to the next |
| **Stop on any failure** | If a verification fails, stop immediately. Report the failure with the exact output you received. Do not attempt to self-correct and continue |
| **No fallback paths** | If a step fails, do not try an alternative approach. Stop and report |
| **Commit exactly as written** | Use the exact commit message from the plan. No rephrasing, no scope changes |
| **No bundling commits** | Each plan gets its own commit as specified. Do not combine them |

---

## Verification Protocol (per phase)

After completing each phase:

1. Run the verification command(s) listed in that phase's **Verify** section
2. Confirm the output matches the expected result
3. If it matches → proceed to next phase
4. If it does not match → **stop and report**. Do not proceed.

---

## Build Verification

After all phases of `DRY_RUN_PLAN.md` are complete and committed:

```bash
ant test
```

Expected: All 26 tests pass. Build exits with code 0.

If any test fails: stop and report the full test output. Do not push.

---

## Final State Checklist

After both plans are fully executed and pushed, confirm each item:

- [ ] `.github/workflows/build.yml` exists and has been pushed to `origin/master`
- [ ] `README.md` contains the CI badge line
- [ ] `md/TODO.md` has both CI and dry-run items moved to `## Done`
- [ ] `ser_sync_config.java` has `isDryRun()` and `setDryRun()` methods
- [ ] `ser_sync_main.java` parses `--dry-run` and guards all 7 write sites
- [ ] `ser-sync.properties.template` has the dry-run comment block appended
- [ ] `ant test` passes (26 tests, exit code 0)
- [ ] `git log --oneline -2` shows exactly two new commits, in this order (newest first):
  1. `feat(cli): add --dry-run flag to preview sync without writing to disk`
  2. `ci: add GitHub Actions workflow to run ant test on push and PR`
- [ ] `git status` is clean

Report the output of `git log --oneline -5` and `ant test` as confirmation that the mission is complete.

---

## What You Are NOT Allowed To Do

- Rename any files
- Refactor any code not mentioned in the plans
- Add tests
- Modify `build.xml`
- Change any file not listed in the plans
- Deviate from the exact commit messages
- Push before `ant test` passes
