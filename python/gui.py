"""
cdd-sync-pro Flet GUI.
Dark mode, SF Pro Display font, 780×760 window.
Phases 1+2: app shell + full config panel.
"""

from __future__ import annotations

import sys
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
        border=ft.border.all(1, _BORDER),
        border_radius=8,
        padding=ft.padding.symmetric(horizontal=12, vertical=10),
    )


def _path_row(
    label: str,
    field: ft.TextField,
    picker: ft.FilePicker | None = None,
) -> ft.Row:
    """Label + text field + optional Browse button row."""
    controls: list[ft.Control] = [
        ft.Text(label, width=120, color=_LABEL, size=12),
        field,
    ]
    if picker is not None:
        controls.append(
            ft.FilledButton(
                "Browse",
                height=36,
                style=ft.ButtonStyle(
                    bgcolor={"": "#454a4a"},
                    color={"": _TEXT},
                    shape={"": ft.RoundedRectangleBorder(radius=6)},
                ),
                on_click=lambda e, p=picker, f=field: _browse(p, f),
            )
        )
    return ft.Row(controls, vertical_alignment=ft.CrossAxisAlignment.CENTER)


def _browse(picker: ft.FilePicker, field: ft.TextField) -> None:
    picker.get_directory_path(initial_directory=field.value or "/")


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
        content_padding=ft.padding.symmetric(horizontal=10, vertical=4),
        expand=True,
    )


def _field(hint: str = "", value: str = "") -> ft.TextField:
    return ft.TextField(
        hint_text=hint,
        value=value,
        height=36,
        text_size=12,
        content_padding=ft.padding.symmetric(horizontal=10, vertical=4),
        border_color=_BORDER,
        focused_border_color=_ACCENT_GREEN,
        cursor_color=_LABEL,
        expand=True,
    )


# ── Main ─────────────────────────────────────────────────────────────────────

def main(page: ft.Page) -> None:
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
    page.padding = ft.padding.all(16)

    # ── File pickers (must be added to page.overlay) ─────────────────────────
    music_picker = ft.FilePicker()
    serato_picker = ft.FilePicker()
    page.overlay.extend([music_picker, serato_picker])

    # ── Path fields ───────────────────────────────────────────────────────────
    music_field = _field("/Volumes/YourDrive/Music")
    serato_field = _field("/Volumes/YourDrive/_Serato_")
    parent_field = _field("e.g. Current  (optional)")

    # Wire pickers → fields
    def _on_music(e: ft.FilePickerResultEvent):
        if e.path:
            music_field.value = e.path
            page.update()

    def _on_serato(e: ft.FilePickerResultEvent):
        if e.path:
            serato_field.value = e.path
            page.update()

    music_picker.on_result = _on_music
    serato_picker.on_result = _on_serato

    # ── Pipeline step checkboxes ─────────────────────────────────────────────
    cb_backup = _checkbox("Backup ✔", value=True)
    cb_clear = _checkbox("Clear Library ⚠️", value=False)
    cb_step1 = _checkbox("Fix Database Paths", value=True)
    cb_step2 = _checkbox("Fix Crate Paths", value=True)
    cb_step3 = _checkbox("Append Existing Crates", value=True)
    cb_step4 = _checkbox("Create New Crates", value=True)
    cb_sort = _checkbox("Reset Crates A→Z", value=False)

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
            page.overlay.append(clear_dlg)
            clear_dlg.open = True
            page.update()

    def _dismiss_clear(confirmed: bool):
        clear_dlg.open = False
        if not confirmed:
            cb_clear.value = False
        page.overlay.remove(clear_dlg)
        page.update()

    cb_clear.on_change = _on_clear_change

    pipeline_grid = ft.Column(
        [
            ft.Row([cb_backup, cb_clear], spacing=24),
            ft.Row([cb_step1, cb_step2], spacing=24),
            ft.Row([cb_step3, cb_step4], spacing=24),
            ft.Row([cb_sort], spacing=0),
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

    # ── Load config if available ──────────────────────────────────────────────
    _load_config(
        music_field, serato_field, parent_field,
        cb_backup, cb_clear, cb_sort,
        cb_step1, cb_step2, cb_step3, cb_step4,
        cb_dupe_scan, dd_detection, dd_move,
    )

    # ── Assemble layout ───────────────────────────────────────────────────────
    page.add(
        ft.Column(
            [
                # Header
                ft.Text(
                    "cdd-sync-pro",
                    size=18,
                    weight=ft.FontWeight.W_600,
                    color=_LABEL,
                ),
                ft.Divider(height=1, color=_BORDER),

                # Paths
                _section(
                    "Paths",
                    ft.Column(
                        [
                            _path_row("Music Folder", music_field, music_picker),
                            _path_row("Serato Path", serato_field, serato_picker),
                            _path_row("Parent Crate", parent_field),
                        ],
                        spacing=8,
                    ),
                ),

                # Pipeline steps
                _section("Pipeline Steps", pipeline_grid),

                # Dupe management
                _section("Duplicate Management", dupe_content),

                # Placeholder for Phase 3 (Start/Cancel + log)
                ft.Container(
                    content=ft.Text("[ Log panel — Phase 3 ]", color="#555555", size=11),
                    bgcolor=_SURFACE,
                    border=ft.border.all(1, _BORDER),
                    border_radius=8,
                    padding=16,
                    expand=True,
                ),
            ],
            spacing=12,
            expand=True,
            scroll=ft.ScrollMode.AUTO,
        )
    )


def _load_config(
    music_field, serato_field, parent_field,
    cb_backup, cb_clear, cb_sort,
    cb_step1, cb_step2, cb_step3, cb_step4,
    cb_dupe_scan, dd_detection, dd_move,
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
    except Exception:
        pass  # Missing or malformed config — start with defaults


def launch() -> None:
    ft.run(main)


if __name__ == "__main__":
    launch()
