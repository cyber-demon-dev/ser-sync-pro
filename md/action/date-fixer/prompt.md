# Agent Prompt: date-fixer Planning Session

Use the `action-plan` skill to produce a full implementation plan for the `date-fixer` feature described below.

---

## Context

`cdd-sync-pro` is a Serato DJ crate sync tool. It already parses the Serato `database V2` binary format — see `md/DATABASE_GUIDE.md` for the full field reference.

The goal of `date-fixer` is a **standalone macOS utility** (modeled after `distr/session-fixer/`) that reads each track's "Date Added" timestamp directly from the Serato database and writes it to the macOS filesystem as the `kMDItemDateAdded` extended attribute — making Finder's "Date Added" column match Serato's "added" column.

---

## Key Technical Decisions (already resolved)

- **Data source**: Serato `database V2` binary — field `uadd` (Unix timestamp Int32) per `otrk` record. No CSV needed.
- **File matching**: `pfil` field in each `otrk` gives the absolute path — no fuzzy matching required.
- **Target metadata**: `com.apple.metadata:kMDItemDateAdded` xattr (binary plist-encoded `NSDate`). This is what Finder reads and sorts by.
- **Why not Date Modified**: Serato touches `st_mtime` during analysis/updates — it would get clobbered. xattr is safe.
- **Why not Date Created**: Valid bonus target (`SetFile -d` or `setattrlist()`), but secondary. Date Added xattr is the primary goal.
- **AWS sync safety**: xattr writes do NOT change file content, size, mtime, or checksums — zero impact on `aws s3 sync`.
- **macOS only**: APFS/HFS+ required for xattr persistence. exFAT volumes should be detected and warned.
- **Cross-user compatibility**: Works for any macOS user; resolves against their configured library root. NFD path normalization may be needed (see DATABASE_GUIDE.md).

---

## Deliverable Shape

A standalone distributable tool at `distr/date-fixer/`, following the `distr/session-fixer/` pattern:

```
distr/date-fixer/
  date-fixer.jar          ← runnable JAR (or equivalent)
  date-fixer.properties   ← user config (Serato DB path, library root)
```

Must include:
- **Dry-run mode** — preview which files would be updated, no writes
- **Drive format check** — warn/skip if target volume is exFAT
- **Skip missing files** — respect `bmis` flag from DB
- **GUI mode** — consistent with session-fixer; simple progress/log output

Optional (nice to have):
- Date Created (`st_birthtime`) write via `SetFile -d` or `setattrlist()` — off by default, toggled in `.properties`

---

## Reference Files

- `md/DATABASE_GUIDE.md` — Serato binary format, all field tags
- `distr/session-fixer/session-fixer.properties` — config pattern to follow
- `README.md` — project overview

---

## Skill Invocation

Run the `action-plan` skill. Produce all standard artefacts in `md/actions/date-fixer/`:
`PLAN.md`, `PHASE_N.md` (one per phase), `ORCHESTRATE.md`, `AUDIT.md`, plus doc + commit wrap-up.
