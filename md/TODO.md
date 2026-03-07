# TODO — cdd-sync-pro

> Backlog and planned work for the project.

---

## Backlog

- [ ] Document Windows-specific path handling and testing notes

## Ideas / Future

- [ ] Support for Rekordbox XML export
- [ ] Web UI for configuration editing
- [ ] Playlist (`.m3u` / `.m3u8`) import/export

## Done

- [x] Add CI pipeline (GitHub Actions) — `ant test` on push/PR to `master`
- [x] Add `--dry-run` CLI flag — previews all sync actions, suppresses all disk writes
- [x] Rename all `ser_sync_*` Java classes → `cdd_sync_*`
