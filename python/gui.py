"""
cdd-sync-pro Flet GUI.
Dark mode, SF Pro Display font, 780×760 window.
"""

from __future__ import annotations

import sys
import threading
from pathlib import Path

import flet as ft

# Ensure python/ is on path so config / sync imports work
_HERE = Path(__file__).parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))


# ── Colour tokens ────────────────────────────────────────────────────────────
_BG = "#1e1e1e"
_SURFACE = "#2b2b2b"
_BORDER = "#3c3f41"
_TEXT = "#bbbbbb"
_LABEL = "#dddddd"
_ACCENT_GREEN = "#5fa85f"
_ACCENT_AMBER = "#c8962a"
_ACCENT_RED = "#c75f5f"


def _section(title: str, content: ft.Control) -> ft.Container:
    """Titled dark section container."""
    return ft.Container(
        content=ft.Column(
            [
                ft.Text(title, size=11, color=_LABEL, weight=ft.FontWeight.W_500),
                ft.Divider(height=1, color=_BORDER),
                content,
            ],
            spacing=6,
        ),
        bgcolor=_SURFACE,
        border=ft.Border(
            left=ft.BorderSide(1, _BORDER),
            top=ft.BorderSide(1, _BORDER),
            right=ft.BorderSide(1, _BORDER),
            bottom=ft.BorderSide(1, _BORDER),
        ),
        border_radius=8,
        padding=ft.Padding(left=12, top=10, right=12, bottom=10),
    )


def _path_row(
    label: str,
    field: ft.TextField,
    on_browse=None,
) -> ft.Row:
    """Label + text field + optional Browse button row."""
    controls: list[ft.Control] = [
        ft.Text(label, width=120, color=_LABEL, size=12),
        field,
    ]
    if on_browse is not None:
        controls.append(
            ft.FilledButton(
                "Browse",
                height=36,
                style=ft.ButtonStyle(
                    bgcolor={"": "#454a4a"},
                    color={"": _TEXT},
                    shape={"": ft.RoundedRectangleBorder(radius=6)},
                ),
                on_click=on_browse,
            )
        )
    return ft.Row(controls, vertical_alignment=ft.CrossAxisAlignment.CENTER)


def _checkbox(label: str, value: bool = True) -> ft.Checkbox:
    return ft.Checkbox(
        label=label,
        value=value,
        label_style=ft.TextStyle(size=12, color=_LABEL),
        fill_color={"selected": _ACCENT_GREEN, "": "#454a4a"},
    )


def _dropdown(options: list[str], value: str) -> ft.Dropdown:
    return ft.Dropdown(
        options=[ft.dropdown.Option(o) for o in options],
        value=value,
        height=36,
        text_size=12,
        content_padding=ft.Padding(left=10, top=4, right=10, bottom=4),
        expand=True,
    )


def _field(hint: str = "", value: str = "") -> ft.TextField:
    return ft.TextField(
        hint_text=hint,
        value=value,
        height=36,
        text_size=12,
        content_padding=ft.Padding(left=10, top=4, right=10, bottom=4),
        border_color=_BORDER,
        focused_border_color=_ACCENT_GREEN,
        cursor_color=_LABEL,
        expand=True,
    )


# ── Main ─────────────────────────────────────────────────────────────────────

async def main(page: ft.Page) -> None:
    # ── Window ───────────────────────────────────────────────────────────────
    page.title = "cdd-sync-pro"
    page.window.width = 780
    page.window.height = 820
    page.window.min_width = 780
    page.window.min_height = 760
    page.window.resizable = True

    # ── Theme ─────────────────────────────────────────────────────────────────
    page.theme_mode = ft.ThemeMode.DARK
    page.theme = ft.Theme(font_family="SF Pro Display")
    page.bgcolor = _BG
    page.padding = ft.Padding(left=16, top=16, right=16, bottom=16)

    # ── File pickers ──────────────────────────────────────────────────────────
    music_picker = ft.FilePicker()
    serato_picker = ft.FilePicker()

    # ── Path fields ─────────────────────────────────────────────────────────
    music_field = _field("/Volumes/YourDrive/Music")
    serato_field = _field("/Volumes/YourDrive/_Serato_")
    parent_field = _field("e.g. Current  (optional)")

    async def _browse_music(_e):
        path = await music_picker.get_directory_path(
            initial_directory=music_field.value or "/"
        )
        if path:
            music_field.value = path
            music_field.update()

    async def _browse_serato(_e):
        path = await serato_picker.get_directory_path(
            initial_directory=serato_field.value or "/"
        )
        if path:
            serato_field.value = path
            serato_field.update()

    # ── Pipeline step checkboxes ─────────────────────────────────────────────
    cb_backup = _checkbox("Backup ✔", value=True)
    cb_clear = _checkbox("Clear Library ⚠️", value=False)
    cb_step1 = _checkbox("Fix Database Paths", value=True)
    cb_step2 = _checkbox("Fix Crate Paths", value=True)
    cb_step3 = _checkbox("Append Existing Crates", value=True)
    cb_step4 = _checkbox("Create New Crates", value=True)
    cb_sort = _checkbox("Reset Crates A→Z", value=False)
    cb_dry_run = ft.Checkbox(
        label="Dry Run (preview only — no writes)",
        value=False,
        label_style=ft.TextStyle(size=12, color=_ACCENT_AMBER),
        fill_color={"selected": _ACCENT_AMBER, "": "#454a4a"},
    )

    # Clear Library confirm dialog
    clear_dlg = ft.AlertDialog(
        modal=True,
        title=ft.Text("⚠️  Destructive Option"),
        content=ft.Text(
            "This will DELETE all existing Serato crates and database V2 before sync.\n\n"
            "All crate structure will be rebuilt from scratch.\n"
            "This cannot be undone (unless Backup is enabled).\n\n"
            "Are you sure?",
            size=13,
        ),
        actions=[
            ft.TextButton("Cancel", on_click=lambda e: _dismiss_clear(False)),
            ft.TextButton(
                "Yes, clear it",
                style=ft.ButtonStyle(color={"": _ACCENT_RED}),
                on_click=lambda e: _dismiss_clear(True),
            ),
        ],
    )

    def _on_clear_change(e: ft.ControlEvent):
        if cb_clear.value:
            page.open(clear_dlg)

    def _dismiss_clear(confirmed: bool):
        if not confirmed:
            cb_clear.value = False
        page.close(clear_dlg)

    cb_clear.on_change = _on_clear_change

    pipeline_grid = ft.Column(
        [
            ft.Row([cb_backup, cb_clear], spacing=24),
            ft.Row([cb_step1, cb_step2], spacing=24),
            ft.Row([cb_step3, cb_step4], spacing=24),
            ft.Row([cb_sort], spacing=0),
            ft.Divider(height=6, color=_BORDER),
            cb_dry_run,
        ],
        spacing=4,
    )

    # ── Dupe management ───────────────────────────────────────────────────────
    cb_dupe_scan = _checkbox("Scan for duplicates", value=False)
    dd_detection = _dropdown(["name-and-size", "name-only", "off"], "name-and-size")
    dd_move = _dropdown(["keep-oldest", "keep-newest", "false"], "false")

    dupe_content = ft.Column(
        [
            cb_dupe_scan,
            ft.Row(
                [
                    ft.Text("Detection:", width=80, color=_LABEL, size=12),
                    dd_detection,
                    ft.Text("Move:", width=40, color=_LABEL, size=12),
                    dd_move,
                ],
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=8,
            ),
        ],
        spacing=6,
    )

    # ── Load config if available; seed defaults if not ────────────────────────
    _cfg_path = Path(__file__).parent / "config.yaml"
    _load_config(
        music_field, serato_field, parent_field,
        cb_backup, cb_clear, cb_sort,
        cb_step1, cb_step2, cb_step3, cb_step4,
        cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
    )
    if not _cfg_path.exists():
        try:
            _build_config(
                music_field, serato_field, parent_field,
                cb_backup, cb_clear, cb_sort,
                cb_step1, cb_step2, cb_step3, cb_step4,
                cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
            ).save(_cfg_path)
        except Exception:
            pass  # Non-fatal — user can still fill paths and click Start

    # ── Refs for dynamic controls ─────────────────────────────────────────────
    _log_ref: ft.Ref[ft.ListView] = ft.Ref()
    _status_ref: ft.Ref[ft.Text] = ft.Ref()
    _start_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _cancel_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _save_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _cancel_event = threading.Event()

    # ── Assemble layout ───────────────────────────────────────────────────────
    page.add(
        ft.Column(
            [
                ft.Text(
                    "cdd-sync-pro",
                    size=18,
                    weight=ft.FontWeight.W_600,
                    color=_LABEL,
                ),
                ft.Divider(height=1, color=_BORDER),
                _section(
                    "Paths",
                    ft.Column(
                        [
                            _path_row("Music Folder", music_field, _browse_music),
                            _path_row("Serato Path", serato_field, _browse_serato),
                            _path_row("Parent Crate", parent_field),
                        ],
                        spacing=8,
                    ),
                ),
                _section("Pipeline Steps", pipeline_grid),
                _section("Duplicate Management", dupe_content),
                _section(
                    "Log Output",
                    ft.ListView(
                        ref=_log_ref,
                        expand=True,
                        auto_scroll=True,
                        spacing=0,
                        height=180,
                    ),
                ),
                ft.Row(
                    [
                        ft.Text(
                            "Ready",
                            ref=_status_ref,
                            size=11,
                            color=_TEXT,
                            expand=True,
                        ),
                        ft.FilledButton(
                            "💾  Save",
                            ref=_save_ref,
                            height=36,
                            style=ft.ButtonStyle(
                                bgcolor={"": "#3a6fa8"},
                                color={"": "#ffffff"},
                                shape={"": ft.RoundedRectangleBorder(radius=6)},
                            ),
                            on_click=lambda e: _on_save(e),
                        ),
                        ft.FilledButton(
                            "▶  Start",
                            ref=_start_ref,
                            height=36,
                            style=ft.ButtonStyle(
                                bgcolor={"": _ACCENT_GREEN},
                                color={"": "#ffffff"},
                                shape={"": ft.RoundedRectangleBorder(radius=6)},
                            ),
                            on_click=lambda e: _on_start(e),
                        ),
                        ft.FilledButton(
                            "✖  Cancel",
                            ref=_cancel_ref,
                            height=36,
                            disabled=True,
                            style=ft.ButtonStyle(
                                bgcolor={"disabled": "#444444", "": _ACCENT_RED},
                                color={"disabled": "#666666", "": "#ffffff"},
                                shape={"": ft.RoundedRectangleBorder(radius=6)},
                            ),
                            on_click=lambda e: _on_cancel(e),
                        ),
                    ],
                    spacing=8,
                    vertical_alignment=ft.CrossAxisAlignment.CENTER,
                ),
            ],
            spacing=12,
            expand=True,
            scroll=ft.ScrollMode.AUTO,
        )
    )

    # ── Handlers ─────────────────────────────────────────────────────────────

    def _append_log(msg: str) -> None:
        """Marshal a log message onto the UI thread."""
        def _do():
            _log_ref.current.controls.append(
                ft.Text(
                    msg,
                    size=11,
                    color=_TEXT,
                    font_family="Courier New",
                    selectable=True,
                )
            )
            page.update()
        page.run_thread(_do)

    def _set_controls_enabled(enabled: bool) -> None:
        """Enable/disable all config controls atomically."""
        for ctrl in [
            music_field, serato_field, parent_field,
            cb_backup, cb_clear, cb_step1, cb_step2, cb_step3, cb_step4, cb_sort,
            cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
        ]:
            ctrl.disabled = not enabled
        _start_ref.current.disabled = not enabled
        _save_ref.current.disabled = not enabled
        _cancel_ref.current.disabled = enabled
        page.update()

    def _on_start(_e) -> None:
        # Validate required fields
        if not music_field.value or not music_field.value.strip():
            page.open(ft.SnackBar(
                content=ft.Text("Music Folder is required."),
                bgcolor=_ACCENT_AMBER,
                open=True,
            ))
            return
        if not serato_field.value or not serato_field.value.strip():
            page.open(ft.SnackBar(
                content=ft.Text("Serato Path is required."),
                bgcolor=_ACCENT_AMBER,
                open=True,
            ))
            return

        # Build config (no save — use 💾 Save for that)
        try:
            cfg = _build_config(
                music_field, serato_field, parent_field,
                cb_backup, cb_clear, cb_sort,
                cb_step1, cb_step2, cb_step3, cb_step4,
                cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
            )
        except Exception as exc:
            page.open(ft.SnackBar(
                content=ft.Text(f"Config error: {exc}"),
                bgcolor=_ACCENT_RED,
                open=True,
            ))
            return

        # Clear log + set UI state
        _log_ref.current.controls.clear()
        _status_ref.current.value = "Dry run running…" if cfg.dry_run else "Running…"
        _set_controls_enabled(False)

        _cancel_event.clear()

        def _worker():
            try:
                from sync.pipeline import run_sync
                run_sync(cfg, log_callback=_append_log)
                def _done():
                    _append_log("✅ Sync Complete")
                    _status_ref.current.value = "Done"
                    _set_controls_enabled(True)
                page.run_thread(_done)
            except Exception as exc:
                def _err():
                    _append_log(f"❌ Sync failed: {exc}")
                    _status_ref.current.value = "Error"
                    _set_controls_enabled(True)
                page.run_thread(_err)

        threading.Thread(target=_worker, daemon=True).start()

    def _on_save(_e) -> None:
        try:
            _build_config(
                music_field, serato_field, parent_field,
                cb_backup, cb_clear, cb_sort,
                cb_step1, cb_step2, cb_step3, cb_step4,
                cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
            ).save(_cfg_path)
            _status_ref.current.value = "Settings saved."
            page.update()
        except Exception as exc:
            page.open(ft.SnackBar(
                content=ft.Text(f"Save failed: {exc}"),
                bgcolor=_ACCENT_RED,
                open=True,
            ))

    def _on_cancel(_e) -> None:
        _cancel_event.set()
        _append_log("[CANCELLED] Stop requested — sync will finish current step.")
        _cancel_ref.current.disabled = True
        page.update()


def _build_config(
    music_field, serato_field, parent_field,
    cb_backup, cb_clear, cb_sort,
    cb_step1, cb_step2, cb_step3, cb_step4,
    cb_dupe_scan, dd_detection, dd_move, cb_dry_run=None,
):
    """Construct a SyncConfig from current GUI control values."""
    from config import SyncConfig
    return SyncConfig(
        music_library_path=(music_field.value or "").strip(),
        serato_library_path=(serato_field.value or "").strip(),
        parent_crate_path=(parent_field.value or "").strip() or None,
        backup_enabled=bool(cb_backup.value),
        clear_library_before_sync=bool(cb_clear.value),
        crate_sorting_enabled=bool(cb_sort.value),
        step1_enabled=bool(cb_step1.value),
        step2_enabled=bool(cb_step2.value),
        step3_enabled=bool(cb_step3.value),
        step4_enabled=bool(cb_step4.value),
        dupe_scan_enabled=bool(cb_dupe_scan.value),
        dupe_detection_mode=dd_detection.value or "off",
        dupe_move_mode=dd_move.value or "false",
        dry_run=bool(cb_dry_run.value) if cb_dry_run is not None else False,
    )


def _load_config(
    music_field, serato_field, parent_field,
    cb_backup, cb_clear, cb_sort,
    cb_step1, cb_step2, cb_step3, cb_step4,
    cb_dupe_scan, dd_detection, dd_move, cb_dry_run=None,
) -> None:
    """Populate controls from config.yaml if it exists. Silent on missing/invalid."""
    try:
        from config import SyncConfig
        cfg_path = Path(__file__).parent / "config.yaml"
        if not cfg_path.exists():
            return
        cfg = SyncConfig.load(cfg_path)
        music_field.value = cfg.music_library_path or ""
        serato_field.value = cfg.serato_library_path or ""
        parent_field.value = cfg.parent_crate_path or ""
        cb_backup.value = cfg.backup_enabled
        cb_clear.value = cfg.clear_library_before_sync
        cb_sort.value = cfg.crate_sorting_enabled
        cb_step1.value = cfg.step1_enabled
        cb_step2.value = cfg.step2_enabled
        cb_step3.value = cfg.step3_enabled
        cb_step4.value = cfg.step4_enabled
        cb_dupe_scan.value = cfg.dupe_scan_enabled
        dd_detection.value = cfg.dupe_detection_mode
        dd_move.value = cfg.dupe_move_mode
        if cb_dry_run is not None:
            cb_dry_run.value = getattr(cfg, "dry_run", False)
    except Exception:
        pass  # Missing or malformed config — start with defaults


def launch() -> None:
    ft.run(main)


if __name__ == "__main__":
    launch()
