# py-bugfix — Audit

| Phase | File(s) | Action | Status |
|-------|---------|--------|--------|
| 1 | `python/gui.py` | Fix `exc` late-binding → NameError on all error paths | ✅ DONE |
| 2 | `python/sync/pipeline.py` | Fix type annotation, incomplete dir clear, missing serato guards | ✅ DONE |
| 3 | `python/config.py` | Fix `newest` alias inversion + remove unused `asdict` import | ✅ DONE |
| 4 | `database_fixer.py`, `serato_parser.py`, `media_library.py` | Remove 3 dead methods | ✅ DONE |
| 5 | `python/sync/pref_sorter.py`, `python/core/path_utils.py` | Atomic pref write + boot-drive path guard | ✅ DONE |
| 6 | all | Commit + push | 🔲 PENDING |

Status: 🔲 PENDING → ⏳ IN FLIGHT → ✅ DONE / ⚠️ DEVIATION
