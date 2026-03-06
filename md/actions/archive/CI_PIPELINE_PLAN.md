# CI Pipeline — GitHub Actions Implementation Plan

> **For:** Another agent to execute.
> **Scope:** Add a GitHub Actions workflow that runs `ant test` on every push and pull request.
> **Constraint:** One file created or modified per phase. No deviations. No fallback paths.

---

## Context

| Item | Value |
|------|-------|
| Build tool | Apache Ant (`build.xml`) |
| Test command | `ant test` |
| Java version | 11 |
| JUnit JAR | `lib/junit-platform-console-standalone-1.11.4.jar` (committed to repo) |
| Existing `.github/` | Contains only `dependabot.yml` — no workflows yet |
| Target trigger | Every push to `master`, every pull request targeting `master` |

---

## Phase 1 — Create the GitHub Actions workflow file

**Action:** Create `.github/workflows/build.yml` with the following exact content.

```yaml
name: Build and Test

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Java 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Install Ant
        run: sudo apt-get install -y ant

      - name: Run tests
        run: ant test
```

**Verify:** File exists at `.github/workflows/build.yml`. Content matches exactly — no extra steps, no caching, no matrix.

---

## Phase 2 — Add README badge

**Action:** Edit `README.md`. Find the first `#` heading line (line 1) and insert the badge on the line immediately after it, followed by a blank line.

The badge markdown to insert:

```markdown
![Build](https://github.com/culprit/ser-sync-pro/actions/workflows/build.yml/badge.svg)
```

> **Note to agent:** Replace `culprit/ser-sync-pro` with the actual GitHub org/repo slug. Confirm with `git remote get-url origin` before editing.

**Verify:** `README.md` contains the badge line. No other lines in the file were changed.

---

## Phase 3 — Update TODO.md

**Action:** Edit `md/TODO.md`. Remove the following line from the Backlog section:

```
- [ ] Add CI pipeline (GitHub Actions) for automated builds
```

Then append a `## Done` section at the bottom of the file (create it if absent) with:

```
- [x] Add CI pipeline (GitHub Actions) — `ant test` on push/PR to `master`
```

**Verify:** The unchecked CI item no longer appears in `## Backlog`. The checked item appears in `## Done`.

---

## Phase 4 — Commit and push

Run the following commands in order. Do not squash or amend.

```bash
git add .github/workflows/build.yml
git add README.md
git add md/TODO.md
git commit -m "ci: add GitHub Actions workflow to run ant test on push and PR"
git push origin master
```

**Verify:** `git status` is clean. `git log --oneline -1` shows the commit. The Actions tab on GitHub shows a workflow run triggered.

---

## Done

All four phases complete when:

1. `.github/workflows/build.yml` exists and is pushed
2. README badge renders on GitHub
3. `md/TODO.md` reflects CI as complete
4. A green workflow run appears in the GitHub Actions tab
