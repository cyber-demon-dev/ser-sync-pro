"""
DatabaseFixer — patches broken file paths in Serato's binary 'database V2' file.

Single-pass TLV walk. For each otrk block, finds pfil tags whose UTF-16BE decoded
path matches a key in path_fixes, then replaces the value, updating both the pfil
and otrk length fields in-place.

Mirrors Java's cdd_sync_database_fixer.updatePaths().
"""

from __future__ import annotations

import logging
import struct
from pathlib import Path
from typing import Dict, List, Tuple

from core.binary_utils import (
    decode_utf16be,
    encode_utf16be,
    read_big_endian_int,
    read_file,
    write_file,
)

logger = logging.getLogger("cdd_sync")

_OTRK = b"otrk"
_PFIL = b"pfil"
_TAG_HEADER = 8  # 4-byte tag + 4-byte big-endian length


def update_paths(database_path: str, path_fixes: Dict[str, str]) -> int:
    """
    Walk *database_path* (binary 'database V2') and replace paths per *path_fixes*.

    Returns count of paths successfully patched. Writes back in-place on any hit.
    Returns 0 and logs on IOError.
    """
    db_path = Path(database_path)

    if not db_path.exists():
        logger.error("database V2 not found: %s", database_path)
        return 0

    try:
        data = bytearray(read_file(db_path))
    except IOError as exc:
        logger.error("Error reading database V2: %s", exc)
        return 0

    # Build otrk index once
    otrk_index = _index_otrk_blocks(data)

    updated = 0

    for old_path, new_path in path_fixes.items():
        old_bytes = encode_utf16be(old_path)
        new_bytes = encode_utf16be(new_path)

        result = _replace_pfil_path(data, old_bytes, new_bytes, otrk_index)
        if result is not None:
            data = result
            updated += 1
            # Rebuild index if byte length changed (otrk offsets have shifted)
            if len(old_bytes) != len(new_bytes):
                otrk_index = _index_otrk_blocks(data)

    if updated > 0:
        try:
            write_file(db_path, bytes(data))
        except IOError as exc:
            logger.error("Error writing database V2: %s", exc)
            return 0

    return updated


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _index_otrk_blocks(data: bytearray) -> List[Tuple[int, int]]:
    """Return list of (otrk_start, otrk_end) pairs."""
    blocks: List[Tuple[int, int]] = []
    pos = 0
    end = len(data)
    while pos < end - _TAG_HEADER:
        if data[pos: pos + 4] == _OTRK:
            length = read_big_endian_int(bytes(data), pos + 4)
            block_end = pos + _TAG_HEADER + length
            blocks.append((pos, block_end))
            pos = block_end
        else:
            pos += 1
    return blocks


def _find_parent_otrk(otrk_index: List[Tuple[int, int]], target_pos: int) -> int:
    """Return the start offset of the otrk block containing *target_pos*, or -1."""
    for start, end in otrk_index:
        if start <= target_pos < end:
            return start
    return -1


def _replace_pfil_path(
    data: bytearray,
    old_bytes: bytes,
    new_bytes: bytes,
    otrk_index: List[Tuple[int, int]],
) -> bytearray | None:
    """
    Find the first pfil tag whose payload matches *old_bytes* and replace it with
    *new_bytes*, adjusting pfil and parent otrk length fields. Returns the updated
    bytearray or None if not found.
    """
    pos = 0
    end = len(data)
    old_len = len(old_bytes)

    while pos + _TAG_HEADER + old_len <= end:
        if data[pos: pos + 4] == _PFIL:
            pfil_len = read_big_endian_int(bytes(data), pos + 4)
            path_start = pos + _TAG_HEADER

            if pfil_len == old_len and path_start + pfil_len <= end:
                if data[path_start: path_start + old_len] == old_bytes:
                    # Match — rebuild buffer
                    length_diff = len(new_bytes) - old_len

                    otrk_pos = _find_parent_otrk(otrk_index, pos)

                    new_pfil_len = len(new_bytes)
                    after_old = path_start + old_len

                    new_data = bytearray(len(data) + length_diff)
                    new_data[:path_start] = data[:path_start]

                    # Patch pfil length
                    struct.pack_into(">I", new_data, pos + 4, new_pfil_len)

                    # Write new path payload
                    new_data[path_start: path_start + new_pfil_len] = new_bytes

                    # Copy remainder
                    new_data[path_start + new_pfil_len:] = data[after_old:]

                    # Patch parent otrk length
                    if otrk_pos != -1:
                        old_otrk_len = read_big_endian_int(bytes(new_data), otrk_pos + 4)
                        struct.pack_into(">I", new_data, otrk_pos + 4, old_otrk_len + length_diff)

                    return new_data
        pos += 1

    return None
