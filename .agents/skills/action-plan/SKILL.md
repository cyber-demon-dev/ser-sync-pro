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

## When to use this skill

Trigger this skill when the user says any of the following (or equivalent):

- "write a plan for another agent"
- "create an action plan"
- "plan + prompt for [feature]"
- "same concept → [feature]"

Read every section of this file before producing any output.

---

## Mandatory Context Sweep

Before writing anything, gather context silently (do not narrate this to the user):

1. Run `git log --oneline -25` — understand recent change patterns, commit conventions, and what was last touched.
2. Read `md/CODEBASE_GUIDE.md` — understand module boundaries and write sites.
3. Read `md/AGENT_LOG.md` — understand what has already been done, what patterns were established.
4. Read `md/TODO.md` — identify which backlog item is being planned.
5. Inspect all source files that the plan will touch — read every file that will be modified, in full.

Do not begin writing until all five steps are complete.

---

## Output: Four Files, Four Steps

All output files go in `md/actions/`. Use `SCREAMING_SNAKE_CASE.md` for filenames.

Create them in this exact order. Do not produce step N+1 until step N is complete and confirmed by the user.

---

### Step 1 — Create the Plan (`*_PLAN.md`)

The plan is executed by another agent. It must require zero interpretation.

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

**Rules for writing the plan:**

- **One file per phase.** Never design a phase that touches two files.
- **Enumerate every write site.** If the feature touches 7 locations in one file, name all 7 with exact replacement code.
- **Include verbatim code.** Never write "add a method like X." Write the exact method.
- **Every phase ends with a Verify block.** The verify must be a runnable command or a concrete observable — not "confirm it compiles."
- **No fallback instructions.** Do not write "if that fails, try Y." There is no Y.
- **Commit is the final phase.** The last phase is always: stage the exact files from the plan, commit with the exact conventional commit message, push.
- **Commit message follows Conventional Commits.** `type(scope): description`. Types: `feat fix refactor perf style docs chore revert test build ci`.

---

### Step 2 — Create the Execution Prompt (`EXECUTE.md`)

The prompt is handed to a fresh agent with no prior context. It must be self-contained.

**Filename:** `md/actions/EXECUTE.md` (overwrite if exists — it is always tailored to the current batch of plans)

**Structure:**

```
# Agent Execution Prompt — <Project>

## Your Mission
[One paragraph. What plans are being executed. What the agent's only job is.]

---

## Mandatory Pre-Flight Protocol
[5 checks minimum: pwd, git status, git branch, plan files exist, plans read in full]

---

## Execution Order
[Ordered list of plan files. No interleaving.]

---

## Strict Execution Rules
[Table. Absolute rules. No exceptions.]

| Rule | Detail |
|------|--------|
| One file per phase | ... |
| No interpretation | ... |
| No additions | ... |
| No consolidation | ... |
| Verify after every phase | ... |
| Stop on any failure | ... |
| No fallback paths | ... |
| Commit exactly as written | ... |
| No bundling commits | ... |

---

## Verification Protocol (per phase)
[Generic: run the Verify block, confirm output, proceed or stop.]

---

## Build Verification
[Project-specific: ant test / npm test / etc. Must pass before push.]

---

## Final State Checklist
[File-by-file checklist. Exact commit messages to verify in git log. git status clean.]

---

## What You Are NOT Allowed To Do
[Explicit prohibition list. At minimum: rename files, refactor unlisted code, add tests, modify build config, deviate from commit messages, push before tests pass.]
```

**Rules for writing the prompt:**

- Every pre-flight step must have an expected output and a "stop and report" instruction on failure.
- Final State Checklist must list every file changed across all plans.
- The "What You Are NOT Allowed To Do" section must close every loophole a helpful agent might exploit.

---

### Step 3 — Create the Audit File (`*_AUDIT.md`)

The audit is filled in **after** the plans are executed (by the user or executing agent). You write the skeleton now; it gets completed later.

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
| 1 | ___ | ___ | ___ | ___ |
| N | ___ | ___ | ___ | ___ |

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

None. / [If any: describe what deviated and why.]

---

## Sign-off

- [ ] All phases passed
- [ ] Build passes
- [ ] git status is clean
- [ ] Pushed to origin/master
```

---

### Step 4 — Update Docs, Commit, Push

After confirming the user is ready, perform these actions in sequence:

1. **Update `md/TODO.md`** — Move the planned feature from `## Backlog` to `## Done` (checked). One file edit.

2. **Update `md/AGENT_LOG.md`** — Append one new entry at the top (below the comment line), using this format:

   ```
   ## YYYY-MM-DD — <Feature Name>

   - **Task**: [one sentence]
   - **Files Changed**:
     - `path/to/file.ext` [NEW/MODIFIED/DELETED] — [one-line reason]
   - **What Was Done**: [2–4 sentences. Results, not intentions.]
   - **Docs to Update**: [list, or "None"]
   ```

   Prune entries older than 14 days from the bottom. Do not delete entries within 14 days.

3. **Stage and commit all four `md/actions/` files + updated docs:**

   ```bash
   git add md/actions/<PLAN>.md md/actions/EXECUTE.md md/actions/<AUDIT>.md
   git add md/TODO.md md/AGENT_LOG.md
   git commit -m "docs(actions): add <feature> plan, prompt, and audit skeleton"
   git push origin master
   ```

   Do not add source code files to this commit. This commit is docs only.

---

## Quality Gates (Self-Check Before Presenting to User)

Before showing any output to the user, verify internally:

| Gate | Check |
|------|-------|
| Plan has zero ambiguous instructions | Every phase has exact code, exact file, exact verify |
| No phase touches more than one file | Count: each phase has exactly one `**File:**` line |
| Every write site is named | Re-read the source. Are there other write sites not in the plan? |
| Verbatim commit message present | Plan ends with a phase containing an exact `git commit -m "..."` |
| EXECUTE.md is self-contained | Could a brand-new agent with no context follow it? |
| Audit skeleton matches the plan | Audit's Phase Execution Log has a row for every phase in the plan |

If any gate fails, fix the output before presenting it.

---

## Naming Conventions

| Artefact | Filename pattern |
|----------|-----------------|
| Plan | `<FEATURE>_PLAN.md` — e.g. `CI_PIPELINE_PLAN.md`, `DRY_RUN_PLAN.md` |
| Prompt | `EXECUTE.md` — always this name, always overwritten |
| Audit | `<FEATURE>_AUDIT.md` — e.g. `CI_PIPELINE_AUDIT.md` |

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

- One concern per commit
- No version-tag prefixes in message body
- `docs(actions):` for the artefact commit (Step 4)
- Feature commit message is specified verbatim in the plan's final phase
