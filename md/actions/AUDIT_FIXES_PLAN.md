# Audit #1 Fixes — Implementation Plan

> For: Another agent to execute.
> Scope: Fix the five highest-concern findings from Audit #1 (two 5s, three 4s) — no new features, no refactors beyond the named write sites.
> Constraint: One file created or modified per phase. No deviations. No fallback paths.

---

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Build command | `ant clean && ant all && ant test` (from project root) |
| Test count baseline | 26 tests passing (confirmed in AGENT_LOG 2026-03-06) |
| Active branch | `master` |
| Source silo — cdd-sync-pro | `cdd-sync-pro/src/` |
| Source silo — shared | `shared/src/` |
| Source silo — session-fixer | `session-fixer/src/` |
| Audit report | `AUDIT_REPORT.md` (brain artefact, findings C5-1, C5-2, C4-1, C4-2, C4-4) |

### Findings Being Fixed

| ID | File | Issue |
|----|------|-------|
| C5-1 | `cdd_sync_crate.java`, `cdd_sync_crate_fixer.java` | Serato filename encoding block copy-pasted 3×. Extract to shared helper. |
| C5-2 | `cdd_sync_crate_scanner.java` | `readInt` reimplemented inline. Replace with `cdd_sync_binary_utils.readInt()`. |
| C4-1 | `cdd_sync_crate_scanner.java` | Silent `IOException` in `parseCrateFile` — no log output. |
| C4-2 | `session_fixer_core_logic.java` | Silent `Exception` catch in `deleteShortSessions` — no log output. |
| C4-4 | `cdd_sync_crate_fixer.java`, `session_fixer_core_logic.java` | Inline NFC normalization duplicated — use `cdd_sync_binary_utils.getFilename()`. |

---

## Phase 1 — Add `resolveSeratoPath()` helper to `cdd_sync_binary_utils`

**File:** `shared/src/cdd_sync_binary_utils.java`

**Action:** Add a new public static method `resolveSeratoPath(String trackPath, cdd_sync_database db)` at the bottom of the class, before the closing `}`. This extracts the 3× duplicated Serato filename encoding logic.

Insert the following block immediately before the final `}` of the class (currently line 111):

```java
    /**
     * Resolves a track path using Serato's original filename encoding from the database.
     * If the database has a Serato-encoded filename for this track, replaces the
     * filesystem filename with the Serato version (preserving encoding for crate writes).
     * Returns the original trackPath unchanged if db is null or no match found.
     */
    public static String resolveSeratoPath(String trackPath, cdd_sync_database db) {
        if (db == null) {
            return trackPath;
        }
        String seratoFilename = db.getSeratoFilename(trackPath);
        if (seratoFilename == null) {
            return trackPath;
        }
        java.io.File f = new java.io.File(trackPath);
        String dir = f.getParent();
        if (dir == null) {
            return trackPath;
        }
        return dir + java.io.File.separator + seratoFilename;
    }
```

**Verify:**

```bash
cd /Users/culprit/Git/cdd-sync-pro && ant clean && ant all 2>&1 | tail -5
```

Expected last line: `BUILD SUCCESSFUL`

---

## Phase 2 — Replace copy #1 and #2 in `cdd_sync_crate.addTrack()` and `cdd_sync_crate.addTracksFiltered()`

**File:** `cdd-sync-pro/src/cdd_sync_crate.java`

**Action:** Replace the duplicated Serato encoding blocks in `addTrack()` (lines 52–65) and `addTracksFiltered()` (lines 91–104) with calls to `cdd_sync_binary_utils.resolveSeratoPath()`.

**In `addTrack()` — replace lines 52–66 (the `trackToAdd` block) with:**

```java
        String trackToAdd = cdd_sync_binary_utils.resolveSeratoPath(trackPath, database);
        tracks.add(trackToAdd);
```

The full `addTrack()` method body after edit:

```java
    public void addTrack(String trackPath) {
        String key = normalizeForDedup(trackPath);
        if (!normalizedPaths.contains(key)) {
            normalizedPaths.add(key);
            String trackToAdd = cdd_sync_binary_utils.resolveSeratoPath(trackPath, database);
            tracks.add(trackToAdd);
        }
    }
```

**In `addTracksFiltered()` — replace lines 87–105 (the for-loop body) with:**

```java
        for (String track : trackPaths) {
            File f = new File(track);
            String size = cdd_sync_binary_utils.formatSize(f.length());
            if (!index.shouldSkipTrack(track, size)) {
                tracks.add(cdd_sync_binary_utils.resolveSeratoPath(track, database));
            }
        }
```

The full `addTracksFiltered()` method body after edit:

```java
    public void addTracksFiltered(Collection<String> trackPaths, cdd_sync_track_index index) {
        if (index == null) {
            tracks.addAll(trackPaths);
            return;
        }
        cdd_sync_database database = index.getDatabase();

        for (String track : trackPaths) {
            File f = new File(track);
            String size = cdd_sync_binary_utils.formatSize(f.length());
            if (!index.shouldSkipTrack(track, size)) {
                tracks.add(cdd_sync_binary_utils.resolveSeratoPath(track, database));
            }
        }
    }
```

**Verify:**

```bash
cd /Users/culprit/Git/cdd-sync-pro && ant clean && ant all && ant test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, `Tests run: 26`, zero failures.

---

## Phase 3 — Replace copy #3 in `cdd_sync_crate_fixer.processCrateFile()` and fix C4-4 inline NFC normalization

**File:** `cdd-sync-pro/src/cdd_sync_crate_fixer.java`

**Action — Part A (C5-1 copy #3):** In `processCrateFile()`, replace the Serato encoding block at lines 206–224 with a call to `cdd_sync_binary_utils.resolveSeratoPath()`.

Locate this block (lines ~206–224):

```java
                    if (database != null) {
                        String originalFilename = database.getSeratoFilename(fixedPath);
                        if (originalFilename != null) {
                            // Get parent directory from the new filesystem location
                            String newDir = new File(fixedPath).getParent();
                            if (newDir != null) {
                                // Strip volume root to make relative path
                                if (volumeRoot != null && newDir.startsWith(volumeRoot)) {
                                    newDir = newDir.substring(volumeRoot.length());
                                    if (newDir.startsWith("/")) {
                                        newDir = newDir.substring(1);
                                    }
                                }
                                // Combine new directory with original filename encoding
                                normalizedPath = newDir + "/" + originalFilename;
                            }
                        }
                    }
```

Replace it with:

```java
                    normalizedPath = cdd_sync_binary_utils.resolveSeratoPath(fixedPath, database);
```

Note: The volume-root stripping portion is intentionally removed — `resolveSeratoPath` returns an absolute path which `cdd_sync_crate.writeTo()` normalizes via `getUniformTrackName()` → `normalizePathForDatabase()`. This is the correct contract.

**Action — Part B (C4-4):** In `fixBrokenPaths()`, replace the inline NFC normalization at line 53:

```java
            String filename = Normalizer.normalize(f.getName().toLowerCase(), Normalizer.Form.NFC);
```

Replace with:

```java
            String filename = cdd_sync_binary_utils.getFilename(path);
```

After this change, the `import java.text.Normalizer;` at line 3 is no longer used in this file. Remove that import line.

**Verify:**

```bash
cd /Users/culprit/Git/cdd-sync-pro && ant clean && ant all && ant test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, `Tests run: 26`, zero failures.

---

## Phase 4 — Fix C5-2: replace inline `readInt` in `cdd_sync_crate_scanner.processTrackBlock()`

**File:** `cdd-sync-pro/src/cdd_sync_crate_scanner.java`

**Action:** In `processTrackBlock()`, replace the manual 4-byte bit-shift at lines 146–149 with `cdd_sync_binary_utils.readInt()`.

Locate this block:

```java
                int len = ((data[i + 4] & 0xFF) << 24) |
                        ((data[i + 5] & 0xFF) << 16) |
                        ((data[i + 6] & 0xFF) << 8) |
                        (data[i + 7] & 0xFF);
```

Replace with:

```java
                int len = cdd_sync_binary_utils.readInt(data, i + 4);
```

**Verify:**

```bash
cd /Users/culprit/Git/cdd-sync-pro && ant clean && ant all && ant test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, `Tests run: 26`, zero failures.

---

## Phase 5 — Fix C4-1 and C4-4 in `cdd_sync_crate_scanner.parseCrateFile()` and `addTrack()`

**File:** `cdd-sync-pro/src/cdd_sync_crate_scanner.java`

**Action — Part A (C4-1):** In `parseCrateFile()`, replace the silent `IOException` catch at lines 72–74:

```java
        } catch (IOException e) {
            // Skip this crate file, continue with others
        }
```

Replace with:

```java
        } catch (IOException e) {
            cdd_sync_log.error("Skipping unreadable crate: " + crateFile.getName() + " — " + e.getMessage());
        }
```

**Action — Part B (C4-4):** In `addTrack()`, the inline NFC normalization call at line 172 reads:

```java
        String normalizedPath = normalizePath(path);
```

This delegates correctly to `cdd_sync_binary_utils.normalizePath()` via the private wrapper `normalizePath()` — no change needed here. However, line 175 reads:

```java
        String filename = cdd_sync_binary_utils.getFilename(path);
```

This is already correct. Confirm these two lines are present and unchanged.

**Verify:**

```bash
cd /Users/culprit/Git/cdd-sync-pro && ant clean && ant all && ant test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, `Tests run: 26`, zero failures.

---

## Phase 6 — Fix C4-2 and C4-4 in `session_fixer_core_logic.deleteShortSessions()`

**File:** `session-fixer/src/session_fixer_core_logic.java`

**Action — Part A (C4-2):** In `deleteShortSessions()`, replace the silent `Exception` catch at lines 288–290:

```java
            } catch (Exception e) {
                // Skip files we can't parse
            }
```

Replace with:

```java
            } catch (cdd_sync_exception e) {
                cdd_sync_log.error("Skipping unreadable session: " + sessionFile.getName() + " — " + e.getMessage());
            } catch (Exception e) {
                cdd_sync_log.error("Unexpected error reading session: " + sessionFile.getName() + " — " + e.getMessage());
            }
```

**Action — Part B (C4-4):** In `fixBrokenPaths()`, replace the inline NFC normalization at line 54:

```java
                String normalizedKey = Normalizer.normalize(filename, Normalizer.Form.NFC).toLowerCase();
```

Replace with:

```java
                String normalizedKey = cdd_sync_binary_utils.getFilename(path);
```

Note: `filename` was defined as `f.getName()` on the line above. With this change, `filename` is no longer needed. Remove the two lines that were:

```java
                String filename = f.getName();
                String normalizedKey = Normalizer.normalize(filename, Normalizer.Form.NFC).toLowerCase();
```

And replace them both with:

```java
                String normalizedKey = cdd_sync_binary_utils.getFilename(path);
```

Also replace the inline NFC normalization in the `fixBrokenPaths()` path-fix lookup block at line 120:

```java
                    String normalizedKey = Normalizer.normalize(filename, Normalizer.Form.NFC).toLowerCase();
```

with:

```java
                    String normalizedKey = cdd_sync_binary_utils.getFilename(trackPath);
```

(The `filename` variable at line 117 `String filename = trackFile.getName();` is then also unused — remove it.)

After both C4-4 changes, if `Normalizer` is no longer referenced anywhere in this file, remove `import java.text.Normalizer;` (line 9).

**Verify:**

```bash
cd /Users/culprit/Git/cdd-sync-pro && ant clean && ant all && ant test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, `Tests run: 26`, zero failures.

---

## Phase 7 — Commit

**File:** (git operation — no source file change)

**Action:** Stage exactly the files changed in Phases 1–6 and commit.

```bash
cd /Users/culprit/Git/cdd-sync-pro
git add shared/src/cdd_sync_binary_utils.java
git add cdd-sync-pro/src/cdd_sync_crate.java
git add cdd-sync-pro/src/cdd_sync_crate_fixer.java
git add cdd-sync-pro/src/cdd_sync_crate_scanner.java
git add session-fixer/src/session_fixer_core_logic.java
git commit -m "refactor(audit): extract resolveSeratoPath, fix readInt dupe, log silent catches"
git push origin master
```

**Verify:**

```bash
git log --oneline -3
```

Expected: top entry contains `refactor(audit): extract resolveSeratoPath, fix readInt dupe, log silent catches`

---

## Done

All phases complete when:

- [ ] `cdd_sync_binary_utils.resolveSeratoPath()` exists and delegates all 3 prior copy-paste sites
- [ ] `cdd_sync_crate_scanner.processTrackBlock()` uses `cdd_sync_binary_utils.readInt()`
- [ ] `parseCrateFile()` IOException logs to `cdd_sync_log.error()`
- [ ] `deleteShortSessions()` catches `cdd_sync_exception` specifically and logs it
- [ ] All inline `Normalizer.normalize(...)` calls replaced with `cdd_sync_binary_utils.getFilename()`
- [ ] `ant test` reports `Tests run: 26`, zero failures
- [ ] Commit pushed to `origin/master`
