---
name: action-plan
description: >
  Use this skill whenever asked to plan, document, or ship a feature —
  especially for another agent to execute. Produces four artefacts in
  md/actions/ : PLAN, PROMPT, AUDIT trail, and doc + commit wrap-up.
  Follows the strict one-file-per-phase, no-deviation protocol established
  in ser-sync-pro.
---

# Action Plan Skill

## When to Use This Skill

Trigger when the user says any of the following (or equivalent):

- "write a plan for another agent"
- "create an action plan"
- "plan + prompt for [feature]"
- "same concept → [feature]"

**Read every section of this file before you produce a single character of output. No exceptions.**

---

## Mandatory Context Sweep

Before you write anything, execute all of the following — silently. Do not narrate this to the user.

1. Run `git log --oneline -25` — understand recent change patterns, commit conventions, and what was last touched.
2. Read `md/CODEBASE_GUIDE.md` — understand module boundaries and write sites.
3. Read `md/AGENT_LOG.md` — understand what has already been done, what patterns were established.
4. Read `md/TODO.md` — identify which backlog item is being planned.
5. Inspect every source file the plan will touch — read them in full.

**Do not begin writing until all five steps are complete. All five. Not four.**

---

## Output: Four Files, Four Steps

All output files go in `md/actions/`. Filenames in `SCREAMING_SNAKE_CASE.md`.

**Do not produce step N+1 until step N is complete and confirmed by the user. One at a time. Move out.**

---

### Step 1 — Create the Plan (`*_PLAN.md`)

This plan will be executed by another agent. It must require **zero interpretation**. If it is ambiguous, it has failed before it started.

**Filename:** `md/actions/<FEATURE_NAME>_PLAN.md`

**Structure:**

```
# <Feature Name> — Implementation Plan

> For: Another agent to execute.
> Scope: [one sentence]
> Constraint: One file created or modified per phase. No deviations. No fallback paths.

---

## Context

| Item | Value |
|------|-------|
| [relevant files, commands, versions, existing state] |

---

## Phase N — <Phase Title>

**File:** `path/to/file.ext`
**Action:** [Precise description. No ambiguity.]

[Exact code to write, verbatim, in a fenced block.]

**Verify:** [Exact shell command or observable outcome that proves it worked.]

---

## Done

All phases complete when:
- [ ] [observable outcome]
- [ ] [observable outcome]
```

**Rules. Non-negotiable. Every single one.**

- **One file per phase.** A phase that touches two files is a failed phase. Fix it before you hand it over.
- **Enumerate every write site.** If the feature touches 7 locations in one file, you name all 7 with exact replacement code. Miss one and the executing agent will deviate. That is your fault.
- **Verbatim code only.** "Add a method like X" is not a plan. It is a suggestion. Write the exact method.
- **Every phase ends with a Verify block.** A runnable command or a concrete observable. "Confirm it compiles" is not a Verify block. It is wishful thinking.
- **No fallback instructions.** Do not write "if that fails, try Y." There is no Y. If it fails, the agent stops and reports.
- **Commit is the final phase.** The last phase is always: stage the exact files from the plan, commit with the exact conventional commit message, push.
- **Commit message follows Conventional Commits.** `type(scope): description`. Types: `feat fix refactor perf style docs chore revert test build ci`.

---

### Step 2 — Create the Execution Prompt (`EXECUTE.md`)

This prompt goes to a fresh agent with zero prior context. It must be fully self-contained. If a brand-new agent cannot follow it without asking a single question, you have failed to write a prompt.

**Filename:** `md/actions/EXECUTE.md` (overwrite if exists — always tailored to the current batch of plans)

**Structure:**

```
# Agent Execution Prompt — <Project>

## Your Mission
[One paragraph. What plans are being executed. What the agent's only job is.]

---

## Mandatory Pre-Flight Protocol
[5 checks minimum: pwd, git status, git branch, plan files exist, plans read in full.
Every check must have an expected output and a "STOP AND REPORT" instruction on failure.]

---

## Execution Order
[Ordered list of plan files. No interleaving. No exceptions.]

---

## Strict Execution Rules

| Rule | Detail |
|------|--------|
| One file per phase | Never create or modify more than one file per phase |
| No interpretation | If the plan says insert after line X, do exactly that. Do not improve it. |
| No additions | No comments, logging, imports, or features not in the plan |
| No consolidation | Do not merge phases or collapse steps, even if they touch the same file |
| Verify after every phase | Run the Verify block. Confirm output. Then and only then proceed. |
| Stop on any failure | Verification fails → stop immediately → report exact output → do not self-correct |
| No fallback paths | A step fails → stop → report. There is no alternative approach. |
| Commit exactly as written | Exact message from the plan. No rephrasing. No scope changes. |
| No bundling commits | Each plan gets its own commit. Do not combine them. |

---

## Verification Protocol (per phase)

1. Run the verification command(s) listed in the phase's **Verify** section.
2. Confirm the output matches the expected result exactly.
3. Match → proceed to the next phase.
4. No match → **STOP AND REPORT.** Do not proceed. Do not attempt to fix it yourself.

---

## Audit Fill-In (Mandatory)

After every phase completes and its Verify block passes, immediately update the
corresponding row in `<FEATURE>_AUDIT.md`:

1. Open the audit file.
2. Fill in `Verify Output` with the actual command output (truncated if long).
3. Set `Pass/Fail` to ✅ PASS or ⚠️ DEVIATION.
4. Deviation → document it in `## Deviations` before touching the next phase.

After all phases and build verification are complete:
- Paste test runner output into `## Build Verification`.
- Paste `git log --oneline -5` into `## Final Commit Log`.
- Check every sign-off box.

**You do not push until every audit sign-off box is checked. Not one box empty.**

---

## Build Verification
[Project-specific: ant test / npm test / etc. Full output. Must pass before push.]

---

## Final State Checklist
[File-by-file checklist. Exact commit messages to verify in git log. git status clean.]

---

## What You Are NOT Allowed To Do
[Explicit prohibition list. At minimum: rename files, refactor unlisted code, add tests,
modify build config, deviate from commit messages, push before tests pass, leave
audit blanks unfilled.]
```

**Rules for writing the prompt:**

- Every pre-flight step has an expected output and a hard "STOP AND REPORT" on failure. No soft language.
- Final State Checklist lists every file changed across all plans.
- The "What You Are NOT Allowed To Do" section closes every loophole a well-meaning agent might exploit. If you can imagine a helpful agent doing something wrong, prohibit it explicitly.

---

### Step 3 — Create the Audit File (`*_AUDIT.md`)

You write the skeleton now. **The executing agent fills it in as they work — one row per phase, immediately after that phase's Verify block passes. The audit is not optional. It is not deferred. It is not filled in at the end in bulk. `EXECUTE.md` contains an explicit section enforcing this because you put it there.**

**Filename:** `md/actions/<FEATURE_NAME>_AUDIT.md`

**Structure:**

```
# <Feature Name> — Execution Audit

> Executor: [agent name or human]
> Date: [YYYY-MM-DD]
> Plans executed: [list plan filenames]

---

## Pre-Flight Results

| Check | Expected | Actual | Pass/Fail |
|-------|----------|--------|-----------|
| pwd | /ser-sync-pro | ___ | ___ |
| git status | clean | ___ | ___ |
| git branch | master | ___ | ___ |
| plan files present | both listed | ___ | ___ |

---

## Phase Execution Log

### Plan: <PLAN_FILENAME>

| Phase | File | Action | Verify Output | Pass/Fail |
|-------|------|--------|---------------|-----------|
| 1 | `<exact file from plan Phase 1>` | `<exact action from plan Phase 1>` | ___ | ___ |
| N | `<exact file from plan Phase N>` | `<exact action from plan Phase N>` | ___ | ___ |

> **Executing agent:** Fill in `Verify Output` and `Pass/Fail` immediately after each phase
> completes. Do not batch-fill at the end. Do not skip this. Do not leave blanks.

---

## Build Verification

```

[paste ant test / test runner output here]

```

Pass/Fail: ___

---

## Final Commit Log

```

[paste git log --oneline -5 output here]

```

---

## Deviations

None. / [If any: describe what deviated and why. Be specific. No vague language.]

---

## Sign-off

- [ ] All phases passed
- [ ] Build passes
- [ ] git status is clean
- [ ] Pushed to origin/master
- [ ] All audit rows filled in — no blanks remaining
```

---

### Step 4 — Update Docs, Commit, Push, Archive

After confirming the user is ready, execute these actions in sequence. Do not skip any.

1. **Update `md/TODO.md`** — Move the planned feature from `## Backlog` to `## Done` (checked). One file edit.

2. **Update `md/AGENT_LOG.md`** — Append one new entry at the top (below the comment line):

   ```
   ## YYYY-MM-DD — <Feature Name>

   - **Task**: [one sentence]
   - **Files Changed**:
     - `path/to/file.ext` [NEW/MODIFIED/DELETED] — [one-line reason]
   - **What Was Done**: [2–4 sentences. Results, not intentions.]
   - **Docs to Update**: [list, or "None"]
   ```

   Prune entries older than 14 days from the bottom. Do not delete entries within 14 days.

3. **Stage and commit artefacts + updated docs:**

   ```bash
   git add md/actions/<PLAN>.md md/actions/EXECUTE.md md/actions/<AUDIT>.md
   git add md/TODO.md md/AGENT_LOG.md
   git commit -m "docs(actions): add <feature> plan, prompt, and audit skeleton"
   git push origin master
   ```

   No source code files in this commit. This is a docs-only commit.

4. **Archive after execution is complete and all audits are signed off.** Move every completed artefact to `md/actions/archive/`:

   ```bash
   git mv md/actions/<PLAN>.md md/actions/archive/<PLAN>.md
   git mv md/actions/<AUDIT>.md md/actions/archive/<AUDIT>.md
   git mv md/actions/EXECUTE.md md/actions/archive/EXECUTE.md
   git commit -m "chore(actions): archive completed <feature> plans, prompts, and audits"
   git push origin master
   ```

   `md/actions/` must be empty — or contain only new in-progress work — after archiving. If it still has signed-off artefacts sitting in it, the job is not done.

---

## Quality Gates — Self-Check Before You Show the User Anything

Run through every gate. If any gate fails, fix the output before presenting it. Do not show the user a plan that fails a quality gate. That is sloppy and unacceptable.

| Gate | What you check |
|------|----------------|
| Zero ambiguous instructions | Every phase has exact code, exact file, exact verify command |
| One file per phase | Count the `**File:**` lines. Each phase has exactly one. |
| Every write site named | Re-read the source. Are there locations not in the plan? Name them or justify why they're excluded. |
| Verbatim commit message | Plan's final phase contains an exact `git commit -m "..."` |
| EXECUTE.md is self-contained | Could a brand-new agent follow it with zero questions? |
| Audit skeleton matches the plan | Every phase in the plan has a corresponding row in the audit. Count them. |
| Audit Fill-In section in EXECUTE.md | EXECUTE.md explicitly tells the executor to fill in the audit per phase |

---

## Naming Conventions

| Artefact | Filename pattern |
|----------|-----------------|
| Plan | `<FEATURE>_PLAN.md` — e.g. `CI_PIPELINE_PLAN.md`, `DRY_RUN_PLAN.md` |
| Prompt | `EXECUTE.md` — always this name, always overwritten |
| Audit | `<FEATURE>_AUDIT.md` — e.g. `CI_PIPELINE_AUDIT.md` |
| Archive | `md/actions/archive/` — all completed plans, audits, and prompts move here after sign-off |

---

## Commit Message Reference

```
feat(scope):     new user-facing feature
fix(scope):      bug fix
refactor(scope): internal restructure, no behavior change
perf(scope):     performance improvement
style(scope):    visual/formatting only
docs(scope):     documentation only
chore(scope):    build, deps, tooling
ci(scope):       CI/CD pipeline changes
test(scope):     tests only
build(scope):    build system changes
```

- One concern per commit. One.
- No version-tag prefixes in message body.
- `docs(actions):` for the artefact commit (Step 4).
- Feature commit message is specified verbatim in the plan's final phase. Copy it exactly.
