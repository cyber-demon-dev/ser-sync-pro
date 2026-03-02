# 🪖 Operation Clean Sweep — Phased Action Plan

> [!IMPORTANT]
> **Agent execution protocol:** Each phase is a self-contained unit. Complete all tasks within a phase before advancing. After every file edit, run `ant compile` from project root to verify. Commit at phase boundaries using the commit message provided. One concern per commit — do not bundle phases.

---

## Phase 1: Git Hygiene

**Risk: Low | Effort: 10 min | Impact: Immediate**

### 1.1 [MODIFY] [.gitignore](file:///Users/culprit/git/ser-sync-pro/.gitignore)

Add the following rule (append, don't replace existing content):

```
# Distribution output
distr/
```

> [!NOTE]
> `.classpath` and `.project` are already covered by existing `.gitignore` rules (L10-11). Only `distr/` is missing.

### 1.2 Remove tracked artifacts from git index

```bash
git rm --cached -r distr/
git rm --cached .classpath .project
```

### 1.3 Verify

```bash
git status                                  # distr/ and IDE files show as deleted from index
git ls-files -- distr/ .classpath .project  # Should return nothing
```

**Commit:** `chore: remove build artifacts and IDE files from tracking`

---

## Phase 2: Shared Utilities (Deduplication)

**Risk: Low | Effort: 1-2 hours | Impact: Eliminates ~12 duplicated methods**

> [!IMPORTANT]
> **Validated duplication counts (corrected from original health check):**
>
> | Method | Files with local definition | Actual count |
> |---|---|---|
> | `readInt(byte[], int)` | `ser_sync_database_entry_selector`, `session_fixer_core_logic`, `session_fixer_parser` + 1 inline in `ser_sync_database.parseTrackData` L132-135 | **3 methods + 1 inline** |
> | `formatSize(long)` | `ser_sync_crate`, `ser_sync_library`, `ser_sync_backup` | **3** |
> | `getFilename(String)` / `extractFilename(String)` | `ser_sync_database`, `ser_sync_crate_scanner`, `ser_sync_database_entry_selector` | **3** |
> | `readFile(File)` | `ser_sync_database_fixer`, `ser_sync_database_entry_selector` | **2** |
> | `writeFile(File, byte[])` | `ser_sync_database_fixer` (only 1 copy — not duplicated) | **1** |
> | `getBooleanOption(String, boolean)` | `ser_sync_config`, `session_fixer_config` | **2** |
>
> `normalizePathForDatabase` in `ser_sync_database_fixer` is **NOT** a duplicate of `normalizePath` — it has different logic (no lowercase, no NFC). Do not consolidate these.

### 2.1 [NEW] [ser_sync_binary_utils.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_binary_utils.java)

Create a utility class with the following **static** methods. Copy the implementation from the first listed source file:

| Method | Signature | Copy from |
|---|---|---|
| `readInt` | `public static int readInt(byte[] data, int offset)` | `ser_sync_database_entry_selector` L161-165 |
| `readFile` | `public static byte[] readFile(File file) throws IOException` | `ser_sync_database_fixer` L255-264 |
| `writeFile` | `public static void writeFile(File file, byte[] data) throws IOException` | `ser_sync_database_fixer` L266-270 |
| `getFilename` | `public static String getFilename(String path)` | `ser_sync_database` L215-222 (includes NFC + lowercase) |
| `getRawFilename` | `public static String getRawFilename(String path)` | `ser_sync_database` L228-233 |
| `formatSize` | `public static String formatSize(long bytes)` | `ser_sync_backup` L111+ |

**Do NOT include:** `normalizePath` or `normalizePathForDatabase` — these vary by call site and should stay local.

### 2.2 [MODIFY] [ser_sync_database_fixer.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_database_fixer.java)

- Delete `readFile()` (L255-264) → replace calls with `ser_sync_binary_utils.readFile()`
- Delete `writeFile()` (L266-270) → replace calls with `ser_sync_binary_utils.writeFile()`
- Replace inline `readInt` at L137-140 and L174-177 and L232-235 with `ser_sync_binary_utils.readInt()`

### 2.3 [MODIFY] [ser_sync_database.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_database.java)

- Delete `getFilename()` (L215-222) → replace calls (L171, L256, L276, L290) with `ser_sync_binary_utils.getFilename()`
- Delete `getRawFilename()` (L228-233) → replace call (L178) with `ser_sync_binary_utils.getRawFilename()`
- Replace inline readInt at L132-135 with `ser_sync_binary_utils.readInt()`

### 2.4 [MODIFY] [ser_sync_backup.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_backup.java)

- Delete `formatSize()` (L108-121) → replace call (L56) with `ser_sync_binary_utils.formatSize()`

### 2.5 [MODIFY] [ser_sync_crate.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_crate.java)

- Delete `formatSize()` (L108-114) → replace call (L98) with `ser_sync_binary_utils.formatSize()`

### 2.6 [MODIFY] [ser_sync_library.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_library.java)

- Delete `formatSize()` (L198-204) → replace call (L73) with `ser_sync_binary_utils.formatSize()`

### 2.7 [MODIFY] [ser_sync_crate_scanner.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_crate_scanner.java)

- Delete `getFilename()` (L200+) → replace calls (L176, L219) with `ser_sync_binary_utils.getFilename()`

### 2.8 [MODIFY] [ser_sync_database_entry_selector.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_database_entry_selector.java)

- Delete `readInt()` (L161+) → replace calls (L92, L119, L135) with `ser_sync_binary_utils.readInt()`
- Delete `readFile()` (L171+) → replace call (L47) with `ser_sync_binary_utils.readFile()`
- Delete `extractFilename()` (L153+) → replace call (L143) with `ser_sync_binary_utils.getFilename()`

### 2.9 [MODIFY] [session_fixer_core_logic.java](file:///Users/culprit/git/ser-sync-pro/session-fixer/src/session_fixer_core_logic.java)

- Delete `readInt()` (L399+) → replace all calls (L316, L329, L343, L348, L349, L356) with `ser_sync_binary_utils.readInt()`

### 2.10 [MODIFY] [session_fixer_parser.java](file:///Users/culprit/git/ser-sync-pro/session-fixer/src/session_fixer_parser.java)

- Delete `readInt()` (L463+) → replace all calls with `ser_sync_binary_utils.readInt()`

### 2.11 Verify after each file

```bash
cd /Users/culprit/git/ser-sync-pro && ant compile
```

**Commit:** `refactor: extract shared binary utilities to ser_sync_binary_utils`

---

## Phase 3: Static State & Lifecycle

**Risk: Medium | Effort: 1-2 hours | Impact: Fixes re-runnability, prevents state leaks**

> [!CAUTION]
> This phase changes fatal error behavior. Manual verification is **mandatory** immediately after completing this phase — do not defer to end. See 3.8.

### 3.1 [NEW] [ser_sync_fatal_exception.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_fatal_exception.java)

Simple `RuntimeException` subclass. Pattern matches existing `ser_sync_exception`:

```java
public class ser_sync_fatal_exception extends RuntimeException {
    public ser_sync_fatal_exception(String message) { super(message); }
    public ser_sync_fatal_exception(String message, Throwable cause) { super(message, cause); }
}
```

### 3.2 [MODIFY] [ser_sync_log.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_log.java)

- `fatalError()` (L166-173): Remove `System.exit(-1)`. Replace with `throw new ser_sync_fatal_exception("Fatal error")` after `closeLogFile()`. The GUI handler (`WINDOW_HANDLER.fatalError()`) should still show the dialog first.
- `success()` (L175-184): Remove `System.exit(0)` from CLI path (L182). Just call `closeLogFile()` in both branches.

### 3.3 [MODIFY] [ser_sync_main.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_main.java)

- Wrap the `runSync()` call in the `SwingWorker` (L62) with try/catch for `ser_sync_fatal_exception` — log and return gracefully.
- Add explicit `System.exit(0)` in the CLI `main()` path only (after `runSync()` completes successfully).

### 3.4 [MODIFY] [session_fixer_main.java](file:///Users/culprit/git/ser-sync-pro/session-fixer/src/session_fixer_main.java)

- Catch `ser_sync_fatal_exception` at `main()` top level — print error, exit cleanly.

### 3.5 [MODIFY] [ser_sync_dupe_mover.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_dupe_mover.java)

Convert static mutable fields (L22-26) to instance fields:

```java
// BEFORE (static):
private static List<String> logEntries = new ArrayList<>();
private static Map<String, String> movedToKeptMap = new HashMap<>();
private static int totalGroupsFound = 0;
private static int totalFilesMoved = 0;
private static String currentMoveMode = ser_sync_config.DUPE_MOVE_KEEP_NEWEST;

// AFTER (instance):
private List<String> logEntries = new ArrayList<>();
private Map<String, String> movedToKeptMap = new HashMap<>();
private int totalGroupsFound = 0;
private int totalFilesMoved = 0;
private String currentMoveMode = ser_sync_config.DUPE_MOVE_KEEP_NEWEST;
```

Keep `scanAndMoveDuplicates()` static — have it create a new instance internally and delegate. Remove the `clear()` call at the top if present (instance creation replaces it).

### 3.6 [MODIFY] [ser_sync_crate_fixer.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_crate_fixer.java)

Replace static `CRATE_POOL` (L23) with a locally-created pool inside `fixBrokenPaths()`. Shutdown after all futures complete:

```java
ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
try {
    // ... submit tasks, collect futures ...
} finally {
    pool.shutdown();
}
```

### 3.7 [MODIFY] [ser_sync_media_library.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_media_library.java)

Same pattern as 3.6: replace static `SCAN_POOL` (L42) with a locally-created `ForkJoinPool` inside `readFrom()`. Shutdown after scanning.

### 3.8 Verify (**mandatory manual testing**)

```bash
cd /Users/culprit/git/ser-sync-pro && ant compile
```

Then verify these three scenarios:

1. **GUI mode + missing config value** → should show error dialog, NOT crash the JVM
2. **CLI mode + missing config value** → should print error and exit cleanly
3. **GUI mode + successful sync** → should allow clicking Start again without restarting

**Commit:** `refactor: eliminate static mutable state and System.exit from library code`

---

## Phase 4: File Organization

**Risk: Low | Effort: 15 min | Impact: Cleaner file boundaries**

### 4.1 [NEW] [ser_sync_log_window_handler.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_log_window_handler.java)

Extract `ser_sync_log_window_handler` class (currently L80-137 in `ser_sync_log_window.java`) to its own file. Change visibility from package-private to `public`.

### 4.2 [MODIFY] [ser_sync_log_window.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_log_window.java)

Remove the `ser_sync_log_window_handler` class definition (L75-137). Only `ser_sync_log_window` should remain.

### 4.3 Verify

```bash
cd /Users/culprit/git/ser-sync-pro && ant compile
```

**Commit:** `refactor: extract log window handler to own file`

---

## Phase 5: Dead Code & Performance

**Risk: Medium | Effort: 1 hour | Impact: Correctness + performance**

### 5.1 [MODIFY] [session_fixer_parser.java](file:///Users/culprit/git/ser-sync-pro/session-fixer/src/session_fixer_parser.java)

In `updatePath()` (L242-295): the first-pass loop (L256-280) builds a `segments` list that is **never used** — `rebuildWithUpdatedPaths()` (L284) re-parses `rawData` from scratch. However, the loop's `replacements` counter IS used (L282).

**Fix:** Keep the counting logic, remove the segment construction:

```java
public int updatePath(String oldPath, String newPath) {
    if (rawData == null || oldPath == null || newPath == null) return 0;
    oldPath = oldPath.replace("\u0000", "");
    newPath = newPath.replace("\u0000", "");

    byte[] oldBytes = toUTF16BE(oldPath);
    int replacements = 0;
    int pos = 0;
    while (pos < rawData.length) {
        int idx = indexOf(rawData, oldBytes, pos);
        if (idx < 0) break;
        replacements++;
        pos = idx + oldBytes.length;
    }

    if (replacements > 0) {
        rawData = rebuildWithUpdatedPaths(oldPath, newPath);
        for (SessionEntry entry : entries) {
            if (oldPath.equals(entry.filepath)) {
                entry.filepath = newPath;
            }
        }
    }
    return replacements;
}
```

### 5.2 [MODIFY] [ser_sync_database_fixer.java](file:///Users/culprit/git/ser-sync-pro/shared/src/ser_sync_database_fixer.java)

**Current behavior (O(N×M)):** `updatePaths()` (L72-91) calls `replacePfilPath()` in a loop. Each call scans the entire byte array from `pos=0` (L130).

**Optimization:** Index all `pfil` positions in a single pass, then apply replacements in reverse order (to preserve earlier positions):

1. Single scan: find all `pfil` markers → build a map of `{path_bytes → pfil_position}`
2. Match against `pathMappings`
3. Apply replacements from end-of-file backward (so position offsets don't cascade)

This changes complexity from O(N×M) to O(M + N log N).

### 5.3 Verify

```bash
cd /Users/culprit/git/ser-sync-pro && ant compile
```

**Commit:** `fix: remove dead code in session parser, optimize batch database path updates`

---

## Phase 6: Config Hardening

**Risk: Low | Effort: 15 min | Impact: Better error handling**

> [!NOTE]
> Depends on Phase 3 (`ser_sync_fatal_exception` must exist).

### 6.1 [MODIFY] [ser_sync_config.java](file:///Users/culprit/git/ser-sync-pro/ser-sync-pro/src/ser_sync_config.java)

In `getRequiredOption()` (L162-167): replace `ser_sync_log.fatalError()` with:

```java
throw new ser_sync_fatal_exception("Required config option missing: " + name);
```

Also fix L71 in `getParentCratePath()`: same replacement (the constructor does NOT call `fatalError`).

### 6.2 [MODIFY] [session_fixer_config.java](file:///Users/culprit/git/ser-sync-pro/session-fixer/src/session_fixer_config.java)

In `getRequiredOption()` (L106-111): same replacement as 6.1.

### 6.3 Verify

```bash
cd /Users/culprit/git/ser-sync-pro && ant compile
```

**Commit:** `fix: config errors throw recoverable exceptions instead of killing JVM`

---

## Phase 7: Tests (JUnit on Ant)

**Risk: Low | Effort: 3-4 hours | Impact: Safety net for binary parsers**

> [!IMPORTANT]
> Skip the Gradle migration. Add JUnit 5 to Ant directly. Zero-dep purity is a feature of this codebase — we're adding ONE test dependency, not a build system migration.

### 7.1 Setup

```bash
mkdir -p /Users/culprit/git/ser-sync-pro/lib
mkdir -p /Users/culprit/git/ser-sync-pro/test
```

Download JUnit 5 standalone console JAR to `lib/`. Add a `<junit>` or `<java>` task to `build.xml`.

### 7.2 [MODIFY] [build.xml](file:///Users/culprit/git/ser-sync-pro/build.xml)

Add a `test` target that:

- Compiles `test/` sources against `shared/src` + `ser-sync-pro/src` + `session-fixer/src` + `lib/*.jar`
- Runs JUnit standalone console launcher

### 7.3 [MODIFY] [.gitignore](file:///Users/culprit/git/ser-sync-pro/.gitignore)

Do **not** ignore `lib/` — we want the JUnit JAR tracked for reproducible builds (it's one file).

### 7.4 [NEW] Tests

| Test file | Tests | Critical paths |
|---|---|---|
| `test/ser_sync_binary_utils_test.java` | `readInt`, `getFilename`, `formatSize` | Edge cases: empty strings, NFD accented chars, Windows paths, `/Volumes/` paths |
| `test/ser_sync_crate_test.java` | Round-trip: create → write → read → assert equals | Empty crate, crate with tracks, accented filenames |
| `test/ser_sync_database_test.java` | Parse a known-good binary fixture → verify track count, path extraction | Requires small binary test fixture |
| `test/session_fixer_parser_test.java` | Round-trip: read → update path → write → read → verify | Path length changes (tests oent/adat length recalculation) |
| `test/ser_sync_database_fixer_test.java` | `replacePfilPath` with synthetic otrk → verify length field updates | Different-length old/new paths |

### 7.5 Verify

```bash
cd /Users/culprit/git/ser-sync-pro && ant test
```

**Commit:** `test: add unit tests for binary parsers and path normalization`

---

## Phase Summary

| Phase | Scope | Files | Risk | Time |
|---|---|---|---|---|
| 1. Git Hygiene | Stop tracking garbage | `.gitignore` + git ops | 🟢 Low | 10 min |
| 2. Shared Utils | Deduplication | 1 new + 9 modified | 🟢 Low | 1-2 hr |
| 3. Static State | Lifecycle fixes | 1 new + 6 modified | 🟡 Medium | 1-2 hr |
| 4. File Organization | Class extraction | 1 new + 1 modified | 🟢 Low | 15 min |
| 5. Dead Code & Perf | Parser + DB fixer | 2 modified | 🟡 Medium | 1 hr |
| 6. Config Hardening | Error recovery | 2 modified | 🟢 Low | 15 min |
| 7. Tests | JUnit on Ant | 2 modified + 5 new | 🟢 Low | 3-4 hr |
| **TOTAL** | | **~25 file ops** | | **~7-10 hr** |

---

## Integration Verification (After All Phases)

Run `ser-sync-pro.jar` against a real (backed-up!) Serato library. Confirm sync behavior is identical to before the refactor.

> [!CAUTION]
> Phase 3 changes fatal error behavior. The 3 manual scenarios in 3.8 **must pass** before proceeding to Phase 4.
