"""
S3 Smart Sync - Database Module
Handles SQLite operations for persisting the local file index (inode tracking).
"""

import sqlite3
import os
from typing import Optional, Dict, List, Tuple
from dataclasses import dataclass


@dataclass
class FileRecord:
    """Represents a file record in the database."""
    path: str
    inode: int
    size: int
    mtime: float
    synced: bool = False


class Database:
    """SQLite database for tracking local file state."""

    def __init__(self, db_path: str):
        self.db_path = db_path
        self.conn: Optional[sqlite3.Connection] = None

    def connect(self) -> None:
        """Initialize database connection and create tables if needed."""
        self.conn = sqlite3.connect(self.db_path)
        self.conn.row_factory = sqlite3.Row
        self._create_tables()

    def _create_tables(self) -> None:
        """Create the file index table if it doesn't exist."""
        cursor = self.conn.cursor()
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS files (
                path TEXT PRIMARY KEY,
                inode INTEGER NOT NULL,
                size INTEGER NOT NULL,
                mtime REAL NOT NULL,
                synced INTEGER DEFAULT 0
            )
        ''')
        cursor.execute('CREATE INDEX IF NOT EXISTS idx_inode ON files(inode)')
        self.conn.commit()

    def get_all_records(self) -> Dict[int, FileRecord]:
        """Get all file records indexed by inode."""
        cursor = self.conn.cursor()
        cursor.execute('SELECT path, inode, size, mtime, synced FROM files')
        result = {}
        for row in cursor.fetchall():
            record = FileRecord(
                path=row['path'],
                inode=row['inode'],
                size=row['size'],
                mtime=row['mtime'],
                synced=bool(row['synced'])
            )
            result[record.inode] = record
        return result

    def get_by_inode(self, inode: int) -> Optional[FileRecord]:
        """Get a file record by its inode."""
        cursor = self.conn.cursor()
        cursor.execute(
            'SELECT path, inode, size, mtime, synced FROM files WHERE inode = ?',
            (inode,)
        )
        row = cursor.fetchone()
        if row:
            return FileRecord(
                path=row['path'],
                inode=row['inode'],
                size=row['size'],
                mtime=row['mtime'],
                synced=bool(row['synced'])
            )
        return None

    def get_by_path(self, path: str) -> Optional[FileRecord]:
        """Get a file record by its path."""
        cursor = self.conn.cursor()
        cursor.execute(
            'SELECT path, inode, size, mtime, synced FROM files WHERE path = ?',
            (path,)
        )
        row = cursor.fetchone()
        if row:
            return FileRecord(
                path=row['path'],
                inode=row['inode'],
                size=row['size'],
                mtime=row['mtime'],
                synced=bool(row['synced'])
            )
        return None

    def upsert(self, record: FileRecord) -> None:
        """Insert or update a file record."""
        cursor = self.conn.cursor()
        cursor.execute('''
            INSERT INTO files (path, inode, size, mtime, synced)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
                inode = excluded.inode,
                size = excluded.size,
                mtime = excluded.mtime,
                synced = excluded.synced
        ''', (record.path, record.inode, record.size, record.mtime, int(record.synced)))
        self.conn.commit()

    def bulk_upsert(self, records: List[FileRecord]) -> None:
        """Insert or update multiple file records."""
        cursor = self.conn.cursor()
        data = [(r.path, r.inode, r.size, r.mtime, int(r.synced)) for r in records]
        cursor.executemany('''
            INSERT INTO files (path, inode, size, mtime, synced)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
                inode = excluded.inode,
                size = excluded.size,
                mtime = excluded.mtime,
                synced = excluded.synced
        ''', data)
        self.conn.commit()

    def delete_by_path(self, path: str) -> None:
        """Delete a file record by path."""
        cursor = self.conn.cursor()
        cursor.execute('DELETE FROM files WHERE path = ?', (path,))
        self.conn.commit()

    def delete_missing_paths(self, current_paths: set) -> List[str]:
        """Delete records for paths that no longer exist locally. Returns deleted paths."""
        cursor = self.conn.cursor()
        cursor.execute('SELECT path FROM files')
        all_paths = {row['path'] for row in cursor.fetchall()}
        missing = all_paths - current_paths
        if missing:
            placeholders = ','.join('?' * len(missing))
            cursor.execute(f'DELETE FROM files WHERE path IN ({placeholders})', tuple(missing))
            self.conn.commit()
        return list(missing)

    def close(self) -> None:
        """Close the database connection."""
        if self.conn:
            self.conn.close()
            self.conn = None
