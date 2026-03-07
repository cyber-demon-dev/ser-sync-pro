# Rename Java Classes: `ser_sync_*` → `cdd_sync_*` — Implementation Plan

> For: Another agent to execute.
> Scope: Rename every `ser_sync_*` Java source file, its public class declaration, and every reference to that class across the entire codebase. `session_fixer_*` files are NOT touched.
> Constraint: One file created or modified per phase. No deviations. No fallback paths.

---

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Build tool | Apache Ant (`build.xml`) |
| Java version | 11 |
| Test runner | JUnit 5 via `ant test` |
| Files in scope | 17 `ser_sync_*` source files + 2 test files + `build.xml` (class name refs only) |
| Files NOT in scope | `session_fixer_*.java`, `build.xml` path/dir properties (already updated in prior rename), `.md` docs |
| Prefix mapping | `ser_sync_` → `cdd_sync_` (underscore, not hyphen) |
| Constraint | Each phase: (1) `git mv` the file, (2) rename `class`/`interface`/`extends`/`implements` declaration inside it, (3) replace every inbound reference to the old class name anywhere in the repo. Do all three steps atomically in one phase. |
| Why one file per phase | In Java, filename must match public class name. Each file rename + its internal self-reference + all inbound cross-references form one atomic unit. Splitting them leaves the build broken between phases. |

> **STOP**: Before Phase 1, confirm `ant test` passes with `BUILD SUCCESSFUL, 26 tests`.
> Run: `ant test 2>&1 | tail -5`
> Expected last line: `BUILD SUCCESSFUL`
> If it fails, STOP AND REPORT. Do not proceed.

---

## Rename Map (complete — 17 source files + 2 test files)

| Old filename | New filename | Old class name | New class name |
|---|---|---|---|
| `shared/src/ser_sync_backup.java` | `shared/src/cdd_sync_backup.java` | `ser_sync_backup` | `cdd_sync_backup` |
| `shared/src/ser_sync_binary_utils.java` | `shared/src/cdd_sync_binary_utils.java` | `ser_sync_binary_utils` | `cdd_sync_binary_utils` |
| `shared/src/ser_sync_database.java` | `shared/src/cdd_sync_database.java` | `ser_sync_database` | `cdd_sync_database` |
| `shared/src/ser_sync_database_fixer.java` | `shared/src/cdd_sync_database_fixer.java` | `ser_sync_database_fixer` | `cdd_sync_database_fixer` |
| `shared/src/ser_sync_exception.java` | `shared/src/cdd_sync_exception.java` | `ser_sync_exception` | `cdd_sync_exception` |
| `shared/src/ser_sync_fatal_exception.java` | `shared/src/cdd_sync_fatal_exception.java` | `ser_sync_fatal_exception` | `cdd_sync_fatal_exception` |
| `shared/src/ser_sync_input_stream.java` | `shared/src/cdd_sync_input_stream.java` | `ser_sync_input_stream` | `cdd_sync_input_stream` |
| `shared/src/ser_sync_log.java` | `shared/src/cdd_sync_log.java` | `ser_sync_log` | `cdd_sync_log` |
| `shared/src/ser_sync_log_window.java` | `shared/src/cdd_sync_log_window.java` | `ser_sync_log_window` | `cdd_sync_log_window` |
| `shared/src/ser_sync_log_window_handler.java` | `shared/src/cdd_sync_log_window_handler.java` | `ser_sync_log_window_handler` | `cdd_sync_log_window_handler` |
| `shared/src/ser_sync_media_library.java` | `shared/src/cdd_sync_media_library.java` | `ser_sync_media_library` | `cdd_sync_media_library` |
| `shared/src/ser_sync_output_stream.java` | `shared/src/cdd_sync_output_stream.java` | `ser_sync_output_stream` | `cdd_sync_output_stream` |
| `cdd-sync-pro/src/ser_sync_config.java` | `cdd-sync-pro/src/cdd_sync_config.java` | `ser_sync_config` | `cdd_sync_config` |
| `cdd-sync-pro/src/ser_sync_crate.java` | `cdd-sync-pro/src/cdd_sync_crate.java` | `ser_sync_crate` | `cdd_sync_crate` |
| `cdd-sync-pro/src/ser_sync_crate_fixer.java` | `cdd-sync-pro/src/cdd_sync_crate_fixer.java` | `ser_sync_crate_fixer` | `cdd_sync_crate_fixer` |
| `cdd-sync-pro/src/ser_sync_crate_scanner.java` | `cdd-sync-pro/src/cdd_sync_crate_scanner.java` | `ser_sync_crate_scanner` | `cdd_sync_crate_scanner` |
| `cdd-sync-pro/src/ser_sync_database_entry_selector.java` | `cdd-sync-pro/src/cdd_sync_database_entry_selector.java` | `ser_sync_database_entry_selector` | `cdd_sync_database_entry_selector` |
| `cdd-sync-pro/src/ser_sync_dupe_mover.java` | `cdd-sync-pro/src/cdd_sync_dupe_mover.java` | `ser_sync_dupe_mover` | `cdd_sync_dupe_mover` |
| `cdd-sync-pro/src/ser_sync_file_utils.java` | `cdd-sync-pro/src/cdd_sync_file_utils.java` | `ser_sync_file_utils` | `cdd_sync_file_utils` |
| `cdd-sync-pro/src/ser_sync_library.java` | `cdd-sync-pro/src/cdd_sync_library.java` | `ser_sync_library` | `cdd_sync_library` |
| `cdd-sync-pro/src/ser_sync_main.java` | `cdd-sync-pro/src/cdd_sync_main.java` | `ser_sync_main` | `cdd_sync_main` |
| `cdd-sync-pro/src/ser_sync_pref_sorter.java` | `cdd-sync-pro/src/cdd_sync_pref_sorter.java` | `ser_sync_pref_sorter` | `cdd_sync_pref_sorter` |
| `cdd-sync-pro/src/ser_sync_pro_window.java` | `cdd-sync-pro/src/cdd_sync_pro_window.java` | `ser_sync_pro_window` | `cdd_sync_pro_window` |
| `cdd-sync-pro/src/ser_sync_track_index.java` | `cdd-sync-pro/src/cdd_sync_track_index.java` | `ser_sync_track_index` | `cdd_sync_track_index` |
| `test/ser_sync_binary_utils_test.java` | `test/cdd_sync_binary_utils_test.java` | `ser_sync_binary_utils_test` | `cdd_sync_binary_utils_test` |
| `test/ser_sync_crate_test.java` | `test/cdd_sync_crate_test.java` | `ser_sync_crate_test` | `cdd_sync_crate_test` |

> **Note on `ser_sync_pro_window`**: Its class name contains `ser_sync_pro_` not just `ser_sync_`. The new name is `cdd_sync_pro_window` — this deliberately mirrors the pattern. The `_pro_` segment stays.

---

## Execution Strategy

Each phase below follows this exact pattern:

```
1. git mv <old-path> <new-path>
2. sed -i '' 's/ser_sync_CLASSNAME/cdd_sync_CLASSNAME/g' <new-path>       # rename class declaration in the file itself
3. find . -name "*.java" -o -name "*.xml" | xargs sed -i '' 's/ser_sync_CLASSNAME/cdd_sync_CLASSNAME/g'   # fix all inbound references
```

> `sed -i ''` is macOS syntax. Do not use `sed -i` (Linux syntax).

The build will be broken between phases. Do NOT run `ant test` between phases — only after Phase 26.

---

## Phase 1 — `ser_sync_exception`

**File:** `shared/src/ser_sync_exception.java` → `shared/src/cdd_sync_exception.java`
**Action:** Rename file and class; update all references. This class is foundational (thrown by I/O streams) so rename it first.

```bash
git mv shared/src/ser_sync_exception.java shared/src/cdd_sync_exception.java
sed -i '' 's/ser_sync_exception/cdd_sync_exception/g' shared/src/cdd_sync_exception.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_exception/cdd_sync_exception/g'
```

**Verify:**

```bash
grep -r "ser_sync_exception" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output (zero hits).

---

## Phase 2 — `ser_sync_fatal_exception`

**File:** `shared/src/ser_sync_fatal_exception.java` → `shared/src/cdd_sync_fatal_exception.java`
**Action:** Rename file and class; update all references.

```bash
git mv shared/src/ser_sync_fatal_exception.java shared/src/cdd_sync_fatal_exception.java
sed -i '' 's/ser_sync_fatal_exception/cdd_sync_fatal_exception/g' shared/src/cdd_sync_fatal_exception.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_fatal_exception/cdd_sync_fatal_exception/g'
```

**Verify:**

```bash
grep -r "ser_sync_fatal_exception" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 3 — `ser_sync_input_stream`

**File:** `shared/src/ser_sync_input_stream.java` → `shared/src/cdd_sync_input_stream.java`
**Action:** Rename file and class; update all references. Referenced by `ser_sync_crate` and `ser_sync_database`.

```bash
git mv shared/src/ser_sync_input_stream.java shared/src/cdd_sync_input_stream.java
sed -i '' 's/ser_sync_input_stream/cdd_sync_input_stream/g' shared/src/cdd_sync_input_stream.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_input_stream/cdd_sync_input_stream/g'
```

**Verify:**

```bash
grep -r "ser_sync_input_stream" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 4 — `ser_sync_output_stream`

**File:** `shared/src/ser_sync_output_stream.java` → `shared/src/cdd_sync_output_stream.java`
**Action:** Rename file and class; update all references. Referenced by `ser_sync_crate`.

```bash
git mv shared/src/ser_sync_output_stream.java shared/src/cdd_sync_output_stream.java
sed -i '' 's/ser_sync_output_stream/cdd_sync_output_stream/g' shared/src/cdd_sync_output_stream.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_output_stream/cdd_sync_output_stream/g'
```

**Verify:**

```bash
grep -r "ser_sync_output_stream" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 5 — `ser_sync_binary_utils`

**File:** `shared/src/ser_sync_binary_utils.java` → `shared/src/cdd_sync_binary_utils.java`
**Action:** Rename file and class; update all references. Also referenced in test file — the test file's own class-level reference to `ser_sync_binary_utils_test` is NOT touched here (handled in Phase 25).

```bash
git mv shared/src/ser_sync_binary_utils.java shared/src/cdd_sync_binary_utils.java
sed -i '' 's/ser_sync_binary_utils/cdd_sync_binary_utils/g' shared/src/cdd_sync_binary_utils.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_binary_utils/cdd_sync_binary_utils/g'
```

> **Warning:** This sed will also touch `test/ser_sync_binary_utils_test.java` — it will replace all calls like `ser_sync_binary_utils.readInt(...)` with `cdd_sync_binary_utils.readInt(...)`, which is correct. The test *file's own class name* (`ser_sync_binary_utils_test`) will also be partially replaced to `cdd_sync_binary_utils_test` — that is expected and correct. The file is not renamed until Phase 25, but the content is correct now.

**Verify:**

```bash
grep -r "ser_sync_binary_utils" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 6 — `ser_sync_log_window`

**File:** `shared/src/ser_sync_log_window.java` → `shared/src/cdd_sync_log_window.java`
**Action:** Rename file and class; update all references. Base class extended by `ser_sync_pro_window` and referenced by `ser_sync_log_window_handler`.

```bash
git mv shared/src/ser_sync_log_window.java shared/src/cdd_sync_log_window.java
sed -i '' 's/ser_sync_log_window/cdd_sync_log_window/g' shared/src/cdd_sync_log_window.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_log_window/cdd_sync_log_window/g'
```

> **Warning:** `ser_sync_log_window_handler` contains the substring `ser_sync_log_window`. The sed pattern `ser_sync_log_window` will match AND replace inside `ser_sync_log_window_handler` occurrences too — producing `cdd_sync_log_window_handler`. That is the correct final name. This is intentional and saves a step.

**Verify:**

```bash
grep -r "ser_sync_log_window" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 7 — `ser_sync_log_window_handler`

**File:** `shared/src/ser_sync_log_window_handler.java` → `shared/src/cdd_sync_log_window_handler.java`

> **Check first:** After Phase 6, open `shared/src/ser_sync_log_window_handler.java`. The Phase 6 sed will have already replaced all `ser_sync_log_window_handler` references inside it with `cdd_sync_log_window_handler`, and all inbound references in other files. The file just needs to be `git mv`'d.

```bash
git mv shared/src/ser_sync_log_window_handler.java shared/src/cdd_sync_log_window_handler.java
```

**Verify:**

```bash
grep -r "ser_sync_log_window_handler" . --include="*.java" --include="*.xml" | grep -v ".git"
# AND confirm the renamed file exists:
ls shared/src/cdd_sync_log_window_handler.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 8 — `ser_sync_log`

**File:** `shared/src/ser_sync_log.java` → `shared/src/cdd_sync_log.java`
**Action:** Rename file and class; update all references. Widely referenced across almost every class.

```bash
git mv shared/src/ser_sync_log.java shared/src/cdd_sync_log.java
sed -i '' 's/ser_sync_log/cdd_sync_log/g' shared/src/cdd_sync_log.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_log/cdd_sync_log/g'
```

> **Warning:** `ser_sync_log_window` and `ser_sync_log_window_handler` are already renamed by this point. The pattern `ser_sync_log` will not produce false matches against them because they no longer exist under `ser_sync_` names.

**Verify:**

```bash
grep -r "ser_sync_log" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 9 — `ser_sync_media_library`

**File:** `shared/src/ser_sync_media_library.java` → `shared/src/cdd_sync_media_library.java`
**Action:** Rename file and class; update all references.

```bash
git mv shared/src/ser_sync_media_library.java shared/src/cdd_sync_media_library.java
sed -i '' 's/ser_sync_media_library/cdd_sync_media_library/g' shared/src/cdd_sync_media_library.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_media_library/cdd_sync_media_library/g'
```

**Verify:**

```bash
grep -r "ser_sync_media_library" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 10 — `ser_sync_backup`

**File:** `shared/src/ser_sync_backup.java` → `shared/src/cdd_sync_backup.java`
**Action:** Rename file and class; update all references.

```bash
git mv shared/src/ser_sync_backup.java shared/src/cdd_sync_backup.java
sed -i '' 's/ser_sync_backup/cdd_sync_backup/g' shared/src/cdd_sync_backup.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_backup/cdd_sync_backup/g'
```

**Verify:**

```bash
grep -r "ser_sync_backup" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 11 — `ser_sync_database`

**File:** `shared/src/ser_sync_database.java` → `shared/src/cdd_sync_database.java`
**Action:** Rename file and class; update all references.

```bash
git mv shared/src/ser_sync_database.java shared/src/cdd_sync_database.java
sed -i '' 's/ser_sync_database/cdd_sync_database/g' shared/src/cdd_sync_database.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_database/cdd_sync_database/g'
```

> **Warning:** `ser_sync_database_fixer`, `ser_sync_database_entry_selector` contain the substring `ser_sync_database`. They have NOT been renamed yet (Phases 12 and 17). The sed `s/ser_sync_database/cdd_sync_database/g` will match inside those longer names too — `ser_sync_database_fixer` → `cdd_sync_database_fixer`, `ser_sync_database_entry_selector` → `cdd_sync_database_entry_selector`. This is the correct final name for both. This is intentional.

**Verify:**

```bash
grep -r "ser_sync_database" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 12 — `ser_sync_database_fixer`

**File:** `shared/src/ser_sync_database_fixer.java` → `shared/src/cdd_sync_database_fixer.java`

> **Check first:** After Phase 11, all `ser_sync_database_fixer` references in other files were already replaced by the Phase 11 sed. The file's own content was also updated. Only the `git mv` remains.

```bash
git mv shared/src/ser_sync_database_fixer.java shared/src/cdd_sync_database_fixer.java
```

**Verify:**

```bash
grep -r "ser_sync_database_fixer" . --include="*.java" --include="*.xml" | grep -v ".git"
ls shared/src/cdd_sync_database_fixer.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 13 — `ser_sync_config`

**File:** `cdd-sync-pro/src/ser_sync_config.java` → `cdd-sync-pro/src/cdd_sync_config.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_config.java cdd-sync-pro/src/cdd_sync_config.java
sed -i '' 's/ser_sync_config/cdd_sync_config/g' cdd-sync-pro/src/cdd_sync_config.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_config/cdd_sync_config/g'
```

**Verify:**

```bash
grep -r "ser_sync_config" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 14 — `ser_sync_crate`

**File:** `cdd-sync-pro/src/ser_sync_crate.java` → `cdd-sync-pro/src/cdd_sync_crate.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_crate.java cdd-sync-pro/src/cdd_sync_crate.java
sed -i '' 's/ser_sync_crate/cdd_sync_crate/g' cdd-sync-pro/src/cdd_sync_crate.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_crate/cdd_sync_crate/g'
```

> **Warning:** `ser_sync_crate_fixer`, `ser_sync_crate_scanner`, `ser_sync_crate_test` contain the substring `ser_sync_crate`. The sed will update all of them to their correct final names. This is intentional.

**Verify:**

```bash
grep -r "ser_sync_crate" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 15 — `ser_sync_crate_fixer`

**File:** `cdd-sync-pro/src/ser_sync_crate_fixer.java` → `cdd-sync-pro/src/cdd_sync_crate_fixer.java`

> **Check first:** After Phase 14, all `ser_sync_crate_fixer` references in other files and inside the file itself were replaced. Only `git mv` remains.

```bash
git mv cdd-sync-pro/src/ser_sync_crate_fixer.java cdd-sync-pro/src/cdd_sync_crate_fixer.java
```

**Verify:**

```bash
grep -r "ser_sync_crate_fixer" . --include="*.java" --include="*.xml" | grep -v ".git"
ls cdd-sync-pro/src/cdd_sync_crate_fixer.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 16 — `ser_sync_crate_scanner`

**File:** `cdd-sync-pro/src/ser_sync_crate_scanner.java` → `cdd-sync-pro/src/cdd_sync_crate_scanner.java`

> **Check first:** After Phase 14, all `ser_sync_crate_scanner` references were replaced. Only `git mv` remains.

```bash
git mv cdd-sync-pro/src/ser_sync_crate_scanner.java cdd-sync-pro/src/cdd_sync_crate_scanner.java
```

**Verify:**

```bash
grep -r "ser_sync_crate_scanner" . --include="*.java" --include="*.xml" | grep -v ".git"
ls cdd-sync-pro/src/cdd_sync_crate_scanner.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 17 — `ser_sync_database_entry_selector`

**File:** `cdd-sync-pro/src/ser_sync_database_entry_selector.java` → `cdd-sync-pro/src/cdd_sync_database_entry_selector.java`

> **Check first:** After Phase 11, all `ser_sync_database_entry_selector` references were replaced. Only `git mv` remains.

```bash
git mv cdd-sync-pro/src/ser_sync_database_entry_selector.java cdd-sync-pro/src/cdd_sync_database_entry_selector.java
```

**Verify:**

```bash
grep -r "ser_sync_database_entry_selector" . --include="*.java" --include="*.xml" | grep -v ".git"
ls cdd-sync-pro/src/cdd_sync_database_entry_selector.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 18 — `ser_sync_dupe_mover`

**File:** `cdd-sync-pro/src/ser_sync_dupe_mover.java` → `cdd-sync-pro/src/cdd_sync_dupe_mover.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_dupe_mover.java cdd-sync-pro/src/cdd_sync_dupe_mover.java
sed -i '' 's/ser_sync_dupe_mover/cdd_sync_dupe_mover/g' cdd-sync-pro/src/cdd_sync_dupe_mover.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_dupe_mover/cdd_sync_dupe_mover/g'
```

**Verify:**

```bash
grep -r "ser_sync_dupe_mover" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 19 — `ser_sync_file_utils`

**File:** `cdd-sync-pro/src/ser_sync_file_utils.java` → `cdd-sync-pro/src/cdd_sync_file_utils.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_file_utils.java cdd-sync-pro/src/cdd_sync_file_utils.java
sed -i '' 's/ser_sync_file_utils/cdd_sync_file_utils/g' cdd-sync-pro/src/cdd_sync_file_utils.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_file_utils/cdd_sync_file_utils/g'
```

**Verify:**

```bash
grep -r "ser_sync_file_utils" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 20 — `ser_sync_library`

**File:** `cdd-sync-pro/src/ser_sync_library.java` → `cdd-sync-pro/src/cdd_sync_library.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_library.java cdd-sync-pro/src/cdd_sync_library.java
sed -i '' 's/ser_sync_library/cdd_sync_library/g' cdd-sync-pro/src/cdd_sync_library.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_library/cdd_sync_library/g'
```

**Verify:**

```bash
grep -r "ser_sync_library" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 21 — `ser_sync_pref_sorter`

**File:** `cdd-sync-pro/src/ser_sync_pref_sorter.java` → `cdd-sync-pro/src/cdd_sync_pref_sorter.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_pref_sorter.java cdd-sync-pro/src/cdd_sync_pref_sorter.java
sed -i '' 's/ser_sync_pref_sorter/cdd_sync_pref_sorter/g' cdd-sync-pro/src/cdd_sync_pref_sorter.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_pref_sorter/cdd_sync_pref_sorter/g'
```

**Verify:**

```bash
grep -r "ser_sync_pref_sorter" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 22 — `ser_sync_track_index`

**File:** `cdd-sync-pro/src/ser_sync_track_index.java` → `cdd-sync-pro/src/cdd_sync_track_index.java`
**Action:** Rename file and class; update all references.

```bash
git mv cdd-sync-pro/src/ser_sync_track_index.java cdd-sync-pro/src/cdd_sync_track_index.java
sed -i '' 's/ser_sync_track_index/cdd_sync_track_index/g' cdd-sync-pro/src/cdd_sync_track_index.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_track_index/cdd_sync_track_index/g'
```

**Verify:**

```bash
grep -r "ser_sync_track_index" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 23 — `ser_sync_pro_window`

**File:** `cdd-sync-pro/src/ser_sync_pro_window.java` → `cdd-sync-pro/src/cdd_sync_pro_window.java`
**Action:** Rename file and class; update all references. Note: new class name is `cdd_sync_pro_window` (keeps `_pro_` segment).

```bash
git mv cdd-sync-pro/src/ser_sync_pro_window.java cdd-sync-pro/src/cdd_sync_pro_window.java
sed -i '' 's/ser_sync_pro_window/cdd_sync_pro_window/g' cdd-sync-pro/src/cdd_sync_pro_window.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_pro_window/cdd_sync_pro_window/g'
```

**Verify:**

```bash
grep -r "ser_sync_pro_window" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

---

## Phase 24 — `ser_sync_main`

**File:** `cdd-sync-pro/src/ser_sync_main.java` → `cdd-sync-pro/src/cdd_sync_main.java`
**Action:** Rename file and class; update all references including `build.xml` `main.class` property.

```bash
git mv cdd-sync-pro/src/ser_sync_main.java cdd-sync-pro/src/cdd_sync_main.java
sed -i '' 's/ser_sync_main/cdd_sync_main/g' cdd-sync-pro/src/cdd_sync_main.java
find . \( -name "*.java" -o -name "*.xml" \) -not -path "./.git/*" | xargs sed -i '' 's/ser_sync_main/cdd_sync_main/g'
```

**Verify:**

```bash
grep -r "ser_sync_main" . --include="*.java" --include="*.xml" | grep -v ".git"
# Confirm build.xml has the updated class name:
grep "main.class" build.xml
```

Expected: grep returns no output. `grep "main.class" build.xml` returns: `<property name="main.class" value="cdd_sync_main"/>`.

---

## Phase 25 — Test file: `ser_sync_binary_utils_test`

**File:** `test/ser_sync_binary_utils_test.java` → `test/cdd_sync_binary_utils_test.java`

> **Check first:** After Phase 5, the file's content (all `cdd_sync_binary_utils` call-site references AND the class name `cdd_sync_binary_utils_test`) was already updated by the Phase 5 sed. Only `git mv` remains.

```bash
git mv test/ser_sync_binary_utils_test.java test/cdd_sync_binary_utils_test.java
```

**Verify:**

```bash
grep -r "ser_sync_binary_utils_test" . --include="*.java" --include="*.xml" | grep -v ".git"
ls test/cdd_sync_binary_utils_test.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 26 — Test file: `ser_sync_crate_test`

**File:** `test/ser_sync_crate_test.java` → `test/cdd_sync_crate_test.java`

> **Check first:** After Phase 14, the file's content (all `cdd_sync_crate` call-site references AND the class name `cdd_sync_crate_test`) was already updated by the Phase 14 sed. Only `git mv` remains.

```bash
git mv test/ser_sync_crate_test.java test/cdd_sync_crate_test.java
```

**Verify:**

```bash
grep -r "ser_sync_crate_test" . --include="*.java" --include="*.xml" | grep -v ".git"
ls test/cdd_sync_crate_test.java
```

Expected: grep returns no output. `ls` returns the file path.

---

## Phase 27 — `build.xml` test class name arguments

**File:** `build.xml`
**Action:** Update the two `<arg value="..."/>` test class name references that were NOT covered by the per-class seds (they reference the test class names explicitly). After Phases 5 and 14, these are already updated. This phase is a confirmation-only verify pass.

```bash
grep "arg value" build.xml | grep -E "cdd_sync_(binary_utils|crate)_test"
```

Expected output (both lines must appear):

```
            <arg value="cdd_sync_binary_utils_test"/>
            <arg value="cdd_sync_crate_test"/>
```

If either still shows `ser_sync_`, run:

```bash
sed -i '' 's/ser_sync_binary_utils_test/cdd_sync_binary_utils_test/g' build.xml
sed -i '' 's/ser_sync_crate_test/cdd_sync_crate_test/g' build.xml
```

**Verify:**

```bash
grep -r "ser_sync_" build.xml | grep -v ".git"
```

Expected: no output.

---

## Phase 28 — Final zero-leak check

**File:** *(no file modified — verification only)*
**Action:** Confirm zero `ser_sync_` identifiers remain in any Java or XML file.

```bash
grep -r "ser_sync_" . --include="*.java" --include="*.xml" | grep -v ".git"
```

Expected: no output.

If any hits remain, STOP AND REPORT. Do not self-correct. Do not proceed to Phase 29.

---

## Phase 29 — Build and test

**File:** *(no file modified — build verification)*

```bash
ant clean && ant all && ant test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` on the final line. All 26 tests pass.

If `BUILD FAILED`, STOP AND REPORT the full `ant test` output. Do not self-correct.

---

## Phase 30 — Commit

**File:** *(git operation — all renamed files staged)*

```bash
git add -A
git commit -m "refactor(classes): rename ser_sync_* classes to cdd_sync_*"
git push origin master
```

**Verify:**

```bash
git log --oneline -3
git status
```

Expected: `git status` is clean. Top commit message is `refactor(classes): rename ser_sync_* classes to cdd_sync_*`.

---

## Done

All phases complete when:

- [ ] Zero `ser_sync_` hits in `grep -r "ser_sync_" . --include="*.java" --include="*.xml"`
- [ ] `ant test` exits `BUILD SUCCESSFUL`, 26 tests pass
- [ ] `git status` is clean
- [ ] `git log --oneline -1` shows `refactor(classes): rename ser_sync_* classes to cdd_sync_*`
- [ ] `distr/cdd-sync-pro/cdd-sync-pro.jar` exists
