"""
cdd-sync-pro CLI entry point.

Usage:
    python main.py [--dry-run]

Loads config.yaml from the current directory, runs the sync pipeline.
Exits 0 on success, 1 on error.
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="cdd-sync-pro: sync filesystem → Serato crates")
    parser.add_argument("--dry-run", action="store_true", help="Log actions but write nothing to disk")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        datefmt="%H:%M:%S",
    )

    config_path = Path("config.yaml")
    if not config_path.exists():
        logging.error("config.yaml not found. Copy config.template.yaml and fill in your paths.")
        sys.exit(1)

    try:
        # Local import so sys.path setup in tests works correctly
        from config import SyncConfig
        from sync.pipeline import run_sync

        cfg = SyncConfig.load(config_path)
        if args.dry_run:
            cfg.dry_run = True

        run_sync(cfg)

    except ValueError as exc:
        logging.error("Config error: %s", exc)
        sys.exit(1)
    except Exception as exc:
        logging.error("Sync failed: %s", exc)
        sys.exit(1)


if __name__ == "__main__":
    main()
