"""
cdd-sync-pro Flet GUI.
Dark mode, SF Pro Display font, 780×760 window.
"""

from __future__ import annotations

import dataclasses
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
    cb_backup = _checkbox("Backup", value=True)
    cb_step1 = _checkbox("Fix Database Paths", value=True)
    cb_step2 = _checkbox("Fix Crate Paths", value=True)
    cb_step3 = _checkbox("Append Existing Crates", value=True)
    cb_step4 = _checkbox("Create New Crates", value=True)
    cb_sort = _checkbox("Reset Crates A→Z", value=False)
    cb_dry_run = ft.Checkbox(
        label="Dry Run",
        value=False,
        label_style=ft.TextStyle(size=12, color=_ACCENT_AMBER),
        fill_color={"selected": _ACCENT_AMBER, "": "#454a4a"},
    )

    # ── Refs for dynamic controls ─────────────────────────────────────────────
    _log_ref: ft.Ref[ft.ListView] = ft.Ref()
    _status_ref: ft.Ref[ft.Text] = ft.Ref()
    _progress_ref: ft.Ref[ft.ProgressBar] = ft.Ref()
    _start_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _cancel_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _save_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _run_backup_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _run_sort_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _run1_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _run2_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _run3_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _run4_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _scan1_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _scan2_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _scan3_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _scan4_ref: ft.Ref[ft.FilledButton] = ft.Ref()
    _cancel_event = threading.Event()

    # Card row style constants
    _CARD_BG = "#313131"
    _CARD_BORDER = "#3f3f3f"

    def _card_step_row(
        cb: ft.Checkbox,
        run_ref: ft.Ref,
        scan_ref: ft.Ref,
        step_n: int,
        step_label: str,
    ) -> ft.Container:
        """Elevated pill card: [checkbox  label──────────  🔍 Scan  ▶ Run]"""
        scan_btn = ft.FilledButton(
            "🔍  Scan",
            ref=scan_ref,
            height=30,
            style=ft.ButtonStyle(
                bgcolor={"": "#1e4a3a"},
                color={"": "#7dc9ae"},
                padding={"": ft.Padding(left=10, top=0, right=10, bottom=0)},
                shape={"": ft.RoundedRectangleBorder(radius=6)},
                text_style={"": ft.TextStyle(size=11, weight=ft.FontWeight.W_500)},
            ),
            on_click=lambda _e, n=step_n: _run_scan_alone(n),
        )
        run_btn = ft.FilledButton(
            "▶  Run",
            ref=run_ref,
            height=30,
            style=ft.ButtonStyle(
                bgcolor={"": "#2d5f96"},
                color={"": "#e0eaf5"},
                padding={"": ft.Padding(left=12, top=0, right=12, bottom=0)},
                shape={"": ft.RoundedRectangleBorder(radius=6)},
                text_style={"": ft.TextStyle(size=11, weight=ft.FontWeight.W_500)},
            ),
            on_click=lambda _e, n=step_n: _run_step_alone(n),
        )
        cb.label = ""  # label lives as a Text control for layout control
        return ft.Container(
            content=ft.Row(
                [
                    cb,
                    ft.Text(
                        step_label,
                        size=12,
                        color=_LABEL,
                        expand=True,
                    ),
                    scan_btn,
                    run_btn,
                ],
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=6,
            ),
            bgcolor=_CARD_BG,
            border=ft.Border(
                left=ft.BorderSide(1, _CARD_BORDER),
                top=ft.BorderSide(1, _CARD_BORDER),
                right=ft.BorderSide(1, _CARD_BORDER),
                bottom=ft.BorderSide(1, _CARD_BORDER),
            ),
            border_radius=8,
            padding=ft.Padding(left=8, top=4, right=8, bottom=4),
        )

    def _card_flag_row(
        *checkboxes: ft.Checkbox,
    ) -> ft.Container:
        """Compact flat pill for flag checkboxes (no run button)."""
        return ft.Container(
            content=ft.Row(
                list(checkboxes),
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=20,
            ),
            bgcolor=_CARD_BG,
            border=ft.Border(
                left=ft.BorderSide(1, _CARD_BORDER),
                top=ft.BorderSide(1, _CARD_BORDER),
                right=ft.BorderSide(1, _CARD_BORDER),
                bottom=ft.BorderSide(1, _CARD_BORDER),
            ),
            border_radius=8,
            padding=ft.Padding(left=8, top=4, right=8, bottom=4),
        )

    def _card_backup_row(cb: ft.Checkbox, ref: ft.Ref) -> ft.Container:
        """Backup row: checkbox + ▶ Run Backup pinned right."""
        run_btn = ft.FilledButton(
            "▶  Run",
            ref=ref,
            height=30,
            style=ft.ButtonStyle(
                bgcolor={"": "#2d5f96"},
                color={"": "#e0eaf5"},
                padding={"": ft.Padding(left=12, top=0, right=12, bottom=0)},
                shape={"": ft.RoundedRectangleBorder(radius=6)},
                text_style={"": ft.TextStyle(size=11, weight=ft.FontWeight.W_500)},
            ),
            on_click=lambda _e: _run_backup_alone(),
        )
        cb.label = ""
        return ft.Container(
            content=ft.Row(
                [
                    cb,
                    ft.Text("Backup", size=12, color=_LABEL, expand=True),
                    run_btn,
                ],
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=6,
            ),
            bgcolor=_CARD_BG,
            border=ft.Border(
                left=ft.BorderSide(1, _CARD_BORDER),
                top=ft.BorderSide(1, _CARD_BORDER),
                right=ft.BorderSide(1, _CARD_BORDER),
                bottom=ft.BorderSide(1, _CARD_BORDER),
            ),
            border_radius=8,
            padding=ft.Padding(left=8, top=4, right=8, bottom=4),
        )

    def _card_sort_row(cb: ft.Checkbox, ref: ft.Ref) -> ft.Container:
        """Reset Crates A→Z row: checkbox + ▶ Run pinned right."""
        run_btn = ft.FilledButton(
            "▶  Run",
            ref=ref,
            height=30,
            style=ft.ButtonStyle(
                bgcolor={"": "#2d5f96"},
                color={"": "#e0eaf5"},
                padding={"": ft.Padding(left=12, top=0, right=12, bottom=0)},
                shape={"": ft.RoundedRectangleBorder(radius=6)},
                text_style={"": ft.TextStyle(size=11, weight=ft.FontWeight.W_500)},
            ),
            on_click=lambda _e: _run_sort_alone(),
        )
        cb.label = ""
        return ft.Container(
            content=ft.Row(
                [
                    cb,
                    ft.Text("Reset Crates A→Z", size=12, color=_LABEL, expand=True),
                    run_btn,
                ],
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=6,
            ),
            bgcolor=_CARD_BG,
            border=ft.Border(
                left=ft.BorderSide(1, _CARD_BORDER),
                top=ft.BorderSide(1, _CARD_BORDER),
                right=ft.BorderSide(1, _CARD_BORDER),
                bottom=ft.BorderSide(1, _CARD_BORDER),
            ),
            border_radius=8,
            padding=ft.Padding(left=8, top=4, right=8, bottom=4),
        )

    pipeline_grid = ft.Column(
        [
            _card_backup_row(cb_backup, _run_backup_ref),
            _card_step_row(cb_step1, _run1_ref, _scan1_ref, 1, "Fix Database Paths"),
            _card_step_row(cb_step2, _run2_ref, _scan2_ref, 2, "Fix Crate Paths"),
            _card_step_row(cb_step3, _run3_ref, _scan3_ref, 3, "Append Existing Crates"),
            _card_step_row(cb_step4, _run4_ref, _scan4_ref, 4, "Create New Crates"),
            _card_sort_row(cb_sort, _run_sort_ref),
        ],
        spacing=5,
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
        cb_backup, cb_sort,
        cb_step1, cb_step2, cb_step3, cb_step4,
        cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
    )
    if not _cfg_path.exists():
        try:
            _build_config(
                music_field, serato_field, parent_field,
                cb_backup, cb_sort,
                cb_step1, cb_step2, cb_step3, cb_step4,
                cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
            ).save(_cfg_path)
        except Exception:
            pass  # Non-fatal — user can still fill paths and click Start

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
                ft.Container(
                    content=ft.Column(
                        [
                            ft.Row(
                                [
                                    ft.Text(
                                        "Pipeline Steps",
                                        size=11,
                                        color=_LABEL,
                                        weight=ft.FontWeight.W_500,
                                        expand=True,
                                    ),
                                    cb_dry_run,
                                ],
                                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                                spacing=0,
                            ),
                            ft.Divider(height=1, color=_BORDER),
                            pipeline_grid,
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
                ),
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
                ft.ProgressBar(
                    ref=_progress_ref,
                    value=None,
                    visible=False,
                    height=3,
                    color="#4a9eff",
                    bgcolor="#2a2a2a",
                    border_radius=2,
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
            cb_backup, cb_step1, cb_step2, cb_step3, cb_step4, cb_sort,
            cb_dupe_scan, dd_detection, dd_move, cb_dry_run,
        ]:
            ctrl.disabled = not enabled
        _start_ref.current.disabled = not enabled
        _save_ref.current.disabled = not enabled
        _cancel_ref.current.disabled = enabled
        if _progress_ref.current:
            _progress_ref.current.visible = not enabled
            _progress_ref.current.value = None if not enabled else 0
        for run_ref in (
            _run_backup_ref, _run_sort_ref,
            _run1_ref, _run2_ref, _run3_ref, _run4_ref,
            _scan1_ref, _scan2_ref, _scan3_ref, _scan4_ref,
        ):
            if run_ref.current:
                run_ref.current.disabled = not enabled
        page.update()

    def _run_backup_alone() -> None:
        """Run backup in isolation on a daemon thread."""
        if not serato_field.value or not serato_field.value.strip():
            page.open(ft.SnackBar(
                content=ft.Text("Serato Path is required."),
                bgcolor=_ACCENT_AMBER,
                open=True,
            ))
            return
        serato_path = serato_field.value.strip()
        _log_ref.current.controls.clear()
        _status_ref.current.value = "Running backup…"
        _set_controls_enabled(False)

        def _worker():
            try:
                from sync.backup import create_backup
                result = create_backup(serato_path)
                def _done():
                    if result:
                        _append_log(f"✅ Backup complete: {result}")
                    else:
                        _append_log("❌ Backup failed — check logs.")
                    _status_ref.current.value = "Done"
                    _set_controls_enabled(True)
                page.run_thread(_done)
            except Exception as exc:
                def _err():
                    _append_log(f"❌ Backup error: {exc}")
                    _status_ref.current.value = "Error"
                    _set_controls_enabled(True)
                page.run_thread(_err)

        threading.Thread(target=_worker, daemon=True).start()

    def _run_sort_alone() -> None:
        """Run sort_crates (Reset A→Z) in isolation on a daemon thread."""
        if not serato_field.value or not serato_field.value.strip():
            page.open(ft.SnackBar(
                content=ft.Text("Serato Path is required."),
                bgcolor=_ACCENT_AMBER,
                open=True,
            ))
            return
        serato_path = serato_field.value.strip()
        _log_ref.current.controls.clear()
        _status_ref.current.value = "Sorting crates…"
        _set_controls_enabled(False)

        def _worker():
            try:
                from sync.pref_sorter import sort_crates
                sort_crates(serato_path)
                def _done():
                    _append_log("✅ Crates sorted A→Z")
                    _status_ref.current.value = "Done"
                    _set_controls_enabled(True)
                page.run_thread(_done)
            except Exception as exc:
                def _err():
                    _append_log(f"❌ Sort failed: {exc}")
                    _status_ref.current.value = "Error"
                    _set_controls_enabled(True)
                page.run_thread(_err)

        threading.Thread(target=_worker, daemon=True).start()

    def _run_step_alone(step_n: int) -> None:
        """Run a single pipeline step in isolation on a daemon thread."""
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
        try:
            cfg = _build_config(
                music_field, serato_field, parent_field,
                cb_backup, cb_sort,
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

        _log_ref.current.controls.clear()
        label = "Dry run" if cfg.dry_run else "Running"
        _status_ref.current.value = f"{label} Step {step_n}…"
        _cancel_event.clear()
        _set_controls_enabled(False)

        def _worker():
            try:
                from sync.pipeline import run_step1, run_step2, run_step3, run_step4
                fn_map = {
                    1: lambda: run_step1(cfg, log_callback=_append_log, cancel_event=_cancel_event),
                    2: lambda: run_step2(cfg, log_callback=_append_log),
                    3: lambda: run_step3(cfg, log_callback=_append_log),
                    4: lambda: run_step4(cfg, log_callback=_append_log),
                }
                fn_map[step_n]()
                def _done():
                    _append_log(f"✅ Step {step_n} complete")
                    _status_ref.current.value = "Done"
                    _set_controls_enabled(True)
                page.run_thread(_done)
            except Exception as exc:
                def _err():
                    _append_log(f"❌ Step {step_n} failed: {exc}")
                    _status_ref.current.value = "Error"
                    _set_controls_enabled(True)
                page.run_thread(_err)

        threading.Thread(target=_worker, daemon=True).start()

    def _run_scan_alone(step_n: int) -> None:
        """Scan a single pipeline step (forced dry-run) on a daemon thread."""
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
        try:
            cfg = _build_config(
                music_field, serato_field, parent_field,
                cb_backup, cb_sort,
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

        # Force dry_run regardless of the checkbox state
        scan_cfg = dataclasses.replace(cfg, dry_run=True)

        _log_ref.current.controls.clear()
        _status_ref.current.value = f"Scanning Step {step_n}…"
        _cancel_event.clear()
        _set_controls_enabled(False)

        def _worker():
            try:
                from sync.pipeline import run_step1, run_step2, run_step3, run_step4
                fn_map = {
                    1: lambda: run_step1(scan_cfg, log_callback=_append_log, cancel_event=_cancel_event),
                    2: lambda: run_step2(scan_cfg, log_callback=_append_log),
                    3: lambda: run_step3(scan_cfg, log_callback=_append_log),
                    4: lambda: run_step4(scan_cfg, log_callback=_append_log),
                }
                fn_map[step_n]()
                def _done():
                    _append_log(f"🔍 Scan complete — no files written")
                    _status_ref.current.value = "Scan complete"
                    _set_controls_enabled(True)
                page.run_thread(_done)
            except Exception as exc:
                def _err():
                    _append_log(f"❌ Scan step {step_n} failed: {exc}")
                    _status_ref.current.value = "Error"
                    _set_controls_enabled(True)
                page.run_thread(_err)

        threading.Thread(target=_worker, daemon=True).start()

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
                cb_backup, cb_sort,
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
                run_sync(cfg, log_callback=_append_log, cancel_event=_cancel_event)
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
                cb_backup, cb_sort,
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
    cb_backup, cb_sort,
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
        clear_library_before_sync=False,
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
    cb_backup, cb_sort,
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
