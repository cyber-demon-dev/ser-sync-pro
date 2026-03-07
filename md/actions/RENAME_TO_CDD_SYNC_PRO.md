# Rename Plan: `ser-sync-pro` → `cdd-sync-pro`

> No code changes in this document. Execute in order. Verify after each phase.

---

## ⚠️ Manual Steps Required (Do These Yourself)

### M1 — Rename the Git repository on GitHub

GitHub doesn't rename repos automatically. You must do this manually:

1. Go to `https://github.com/cyber-demon-dev/ser-sync-pro`
2. **Settings** → **General** → **Repository name** → change to `cdd-sync-pro`
3. Click **Rename**

GitHub will redirect the old URL for a while, but update your remote anyway (see M2).

---

### M2 — Update your local git remote

After renaming on GitHub, run in terminal:

```bash
git remote set-url origin https://github.com/cyber-demon-dev/cdd-sync-pro.git
```

Verify:

```bash
git remote -v
```

---

### M3 — Rename the root folder on disk

The repo root is currently `/Users/culprit/git/ser-sync-pro`. Rename it:

```bash
mv /Users/culprit/git/ser-sync-pro /Users/culprit/git/cdd-sync-pro
```

Then reopen the project in your IDE from the new path.

---

## Agent-Executable Changes

The following are safe for an agent to execute. All are find-and-replace or file/folder renames within the repo.

---

### Phase 1 — Rename the source silo folder

| Action | From | To |
|--------|------|----|
| Rename directory | `ser-sync-pro/` (the source silo) | `cdd-sync-pro/` |
| Rename file | `ser-sync-pro/ser-sync.properties.template` | `cdd-sync-pro/ser-sync.properties.template` |

> **Note**: The `session-fixer/` and `shared/` silos are NOT renamed — they are independent tools and keep their names.

---

### Phase 2 — `build.xml` (12 occurrences)

Update all `ser-sync-pro` references:

| Property / attribute | Old value | New value |
|----------------------|-----------|-----------|
| `project name` | `ser-sync-pro` | `cdd-sync-pro` |
| `src.dir` | `ser-sync-pro/src` | `cdd-sync-pro/src` |
| `build.dir` | `out/production/ser-sync-pro` | `out/production/cdd-sync-pro` |
| `dist.dir` | `distr/ser-sync-pro` | `distr/cdd-sync-pro` |
| `jar.name` | `ser-sync-pro.jar` | `cdd-sync-pro.jar` |
| `props.template` | `ser-sync-pro/ser-sync.properties.template` | `cdd-sync-pro/ser-sync.properties.template` |
| Javac `srcdir` path component | `ser-sync-pro/src` | `cdd-sync-pro/src` |
| Test classpath entry | `out/production/ser-sync-pro` | `out/production/cdd-sync-pro` |
| Test `arg value` | `out/test-classes:out/production/ser-sync-pro` | `out/test-classes:out/production/cdd-sync-pro` |
| Description strings (3×) | `ser-sync-pro` | `cdd-sync-pro` |

---

### Phase 3 — Java source files

#### `ser-sync-pro/src/ser_sync_main.java` (2 occurrences)

| Line | Old | New |
|------|-----|-----|
| 109 | `"ser-sync-pro/logs"` | `"cdd-sync-pro/logs"` |
| 112 | `"ser-sync-pro started"` | `"cdd-sync-pro started"` |

> **Impact**: This controls the on-disk folder name where logs are written (`<volume>/cdd-sync-pro/logs/`) and the startup log message. Existing `ser-sync-pro/logs/` folders on user volumes will be abandoned — new logs go to `cdd-sync-pro/logs/` going forward.

#### `ser-sync-pro/src/ser_sync_dupe_mover.java` (1 occurrence)

| Line | Old | New |
|------|-----|-----|
| 21 | `"ser-sync-pro/dupes"` | `"cdd-sync-pro/dupes"` |

> **Impact**: Moved duplicate files will go to `<volume>/cdd-sync-pro/dupes/<timestamp>/` instead of `ser-sync-pro/dupes/`. Any existing dupes folders on disk are unaffected (historical data stays where it is).

#### `ser-sync-pro/src/ser_sync_pro_window.java` (3 occurrences)

| Line | Old | New |
|------|-----|-----|
| 53 | Window title: `"ser-sync-pro"` | `"cdd-sync-pro"` |
| 375 | Properties comment: `"ser-sync-pro configuration"` | `"cdd-sync-pro configuration"` |
| Javadoc line 9 | `"ser-sync-pro"` | `"cdd-sync-pro"` |

---

### Phase 4 — Configuration template

#### `ser-sync-pro/ser-sync.properties.template` (1 occurrence)

| Line 51 | Old | New |
|---------|-----|-----|
| Comment | `java -jar ser-sync-pro.jar --dry-run` | `java -jar cdd-sync-pro.jar --dry-run` |

Also update the comment header on line 1 of `distr/ser-sync-pro/ser-sync.properties` if it exists (generated artifact — may be stale after `ant clean`).

---

### Phase 5 — Documentation (`md/`)

All references in these files are string replacements only:

| File | Occurrences |
|------|-------------|
| `md/CODEBASE_GUIDE.md` | ~20 (titles, directory tree, module table, flow diagram) |
| `md/AGENT_LOG.md` | ~15 (file path references in log entries) |
| `md/TODO.md` | 1 (header) |
| `md/CONCEPTS.md` | 1 (header) |
| `md/DATABASE_GUIDE.md` | 1 (link text) |
| `README.md` (root) | ~15 (title, badge URL, paths, JAR names, directory tree) |

> **Badge URL in README.md** — line 3 references the GitHub Actions badge:
> `https://github.com/cyber-demon-dev/ser-sync-pro/actions/workflows/build.yml`
> This must become:
> `https://github.com/cyber-demon-dev/cdd-sync-pro/actions/workflows/build.yml`
> This only works correctly **after** M1 (GitHub repo rename) is done.

---

### Phase 6 — `.classpath` / `.project` (IDE files)

These Eclipse/IntelliJ project files may reference the project name:

| File | Check for |
|------|-----------|
| `.classpath` | Any path containing `ser-sync-pro` |
| `.project` | `<name>` tag value |

Update `<name>ser-sync-pro</name>` → `<name>cdd-sync-pro</name>` in `.project` if present.

---

### Phase 7 — Rebuild & verify

```bash
ant clean
ant all
ant test
```

Expected: `BUILD SUCCESSFUL`, 26 tests pass, `distr/cdd-sync-pro/cdd-sync-pro.jar` exists.

---

## What Does NOT Change

| Item | Reason |
|------|--------|
| All Java class/file names (`ser_sync_*.java`) | These are the class names — a separate rename if ever desired |
| `session-fixer/` directory and its contents | Independent silo, unrelated to the brand rename |
| `shared/` directory | Shared infrastructure, no product name baked in |
| On-disk Serato data (`_Serato_`, `.crate` files) | Not part of this tool's naming |
| Existing `ser-sync-pro/` folders on user volumes | Historical data — do not move automatically |

---

## Commit

```
refactor: rename project from ser-sync-pro to cdd-sync-pro
```
