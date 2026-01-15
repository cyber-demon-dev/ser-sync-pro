"""
S3 Smart Sync - S3 Client Module
Wraps boto3 for S3 operations including upload, delete, and server-side move.
"""

import os
import boto3
from botocore.exceptions import ClientError
from typing import List, Optional, Dict
from dataclasses import dataclass


@dataclass
class S3Object:
    """Represents an object in S3."""
    key: str
    size: int
    etag: str


class S3Client:
    """
    Wrapper for boto3 S3 operations.
    
    Provides upload, delete, list, and most importantly, server-side move
    which uses copy+delete to relocate objects without re-uploading.
    """

    def __init__(self, bucket_name: str, prefix: str = ""):
        """
        Initialize S3 client.
        
        Args:
            bucket_name: Name of the S3 bucket.
            prefix: Optional prefix (folder) within the bucket.
        """
        self.bucket_name = bucket_name
        self.prefix = prefix.strip('/') + '/' if prefix else ''
        self.client = boto3.client('s3')
        self.resource = boto3.resource('s3')
        self.bucket = self.resource.Bucket(bucket_name)

    def _full_key(self, key: str) -> str:
        """Prepend prefix to key."""
        return f"{self.prefix}{key}"

    def list_objects(self) -> Dict[str, S3Object]:
        """
        List all objects in the bucket (under prefix).
        
        Returns:
            Dict mapping key (without prefix) -> S3Object
        """
        result = {}
        paginator = self.client.get_paginator('list_objects_v2')
        
        for page in paginator.paginate(Bucket=self.bucket_name, Prefix=self.prefix):
            for obj in page.get('Contents', []):
                key = obj['Key']
                # Remove prefix for relative key
                rel_key = key[len(self.prefix):] if key.startswith(self.prefix) else key
                if rel_key:  # Skip empty keys (the prefix folder itself)
                    result[rel_key] = S3Object(
                        key=rel_key,
                        size=obj['Size'],
                        etag=obj['ETag'].strip('"')
                    )
        return result

    def upload_file(self, local_path: str, key: str) -> bool:
        """
        Upload a file to S3.
        
        Args:
            local_path: Absolute path to local file.
            key: S3 key (relative, prefix will be added).
            
        Returns:
            True if successful, False otherwise.
        """
        full_key = self._full_key(key)
        try:
            self.client.upload_file(local_path, self.bucket_name, full_key)
            print(f"  [UPLOAD] {key}")
            return True
        except ClientError as e:
            print(f"  [ERROR] Upload failed for {key}: {e}")
            return False

    def delete_object(self, key: str) -> bool:
        """
        Delete an object from S3.
        
        Args:
            key: S3 key (relative, prefix will be added).
            
        Returns:
            True if successful, False otherwise.
        """
        full_key = self._full_key(key)
        try:
            self.client.delete_object(Bucket=self.bucket_name, Key=full_key)
            print(f"  [DELETE] {key}")
            return True
        except ClientError as e:
            print(f"  [ERROR] Delete failed for {key}: {e}")
            return False

    def delete_objects(self, keys: List[str]) -> int:
        """
        Delete multiple objects from S3 (batch delete).
        
        Args:
            keys: List of S3 keys to delete.
            
        Returns:
            Number of successfully deleted objects.
        """
        if not keys:
            return 0
        
        # S3 batch delete supports up to 1000 objects per request
        deleted_count = 0
        for i in range(0, len(keys), 1000):
            batch = keys[i:i+1000]
            delete_objects = [{'Key': self._full_key(k)} for k in batch]
            try:
                response = self.client.delete_objects(
                    Bucket=self.bucket_name,
                    Delete={'Objects': delete_objects}
                )
                deleted = len(response.get('Deleted', []))
                deleted_count += deleted
                for item in response.get('Deleted', []):
                    key = item['Key']
                    rel_key = key[len(self.prefix):] if key.startswith(self.prefix) else key
                    print(f"  [DELETE] {rel_key}")
            except ClientError as e:
                print(f"  [ERROR] Batch delete failed: {e}")
        
        return deleted_count

    def move_object(self, old_key: str, new_key: str, archive_handler=None) -> str:
        """
        Move an object within S3 using server-side copy + delete.
        
        This is the key bandwidth-saving operation. The data never leaves AWS.
        
        Args:
            old_key: Current S3 key (relative).
            new_key: New S3 key (relative).
            archive_handler: Optional ArchiveHandler for logging archived objects.
            
        Returns:
            'success', 'archived', or 'error'
        """
        full_old_key = self._full_key(old_key)
        full_new_key = self._full_key(new_key)
        
        try:
            # Server-side copy
            copy_source = {'Bucket': self.bucket_name, 'Key': full_old_key}
            self.client.copy_object(
                Bucket=self.bucket_name,
                Key=full_new_key,
                CopySource=copy_source
            )
            
            # Delete original
            self.client.delete_object(Bucket=self.bucket_name, Key=full_old_key)
            
            print(f"  [MOVE] {old_key} -> {new_key}")
            return 'success'
        except ClientError as e:
            error_code = e.response.get('Error', {}).get('Code', '')
            if error_code == 'InvalidObjectState':
                # Object is in Glacier/Deep Archive - needs restoration
                if archive_handler:
                    archive_handler.log_archived_key(old_key, str(e))
                else:
                    print(f"  [ARCHIVED] {old_key} - requires restore (Glacier/Deep Archive)")
                return 'archived'
            else:
                print(f"  [ERROR] Move failed for {old_key} -> {new_key}: {e}")
                return 'error'

    def restore_object(self, key: str, days: int = 7, tier: str = 'Bulk') -> bool:
        """
        Initiate a restore request for an archived object.
        
        Args:
            key: S3 key (relative).
            days: Number of days to keep the restored copy available.
            tier: Restore tier - 'Bulk' (12-48h), 'Standard' (3-5h), or 'Expedited' (1-5min).
            
        Returns:
            True if restore initiated/already in progress, False on error.
        """
        full_key = self._full_key(key)
        
        try:
            self.client.restore_object(
                Bucket=self.bucket_name,
                Key=full_key,
                RestoreRequest={
                    'Days': days,
                    'GlacierJobParameters': {
                        'Tier': tier
                    }
                }
            )
            print(f"  [RESTORE] Initiated for {key} (tier: {tier})")
            return True
        except ClientError as e:
            error_code = e.response.get('Error', {}).get('Code', '')
            if error_code == 'RestoreAlreadyInProgress':
                print(f"  [RESTORE] Already in progress: {key}")
                return True
            else:
                print(f"  [ERROR] Restore failed for {key}: {e}")
                return False

    def get_restore_status(self, key: str) -> dict:
        """
        Check the restore status of an archived object.
        
        Args:
            key: S3 key (relative).
            
        Returns:
            Dict with 'storage_class', 'restore_status' ('not_started', 'in_progress', 'completed')
        """
        full_key = self._full_key(key)
        
        try:
            response = self.client.head_object(Bucket=self.bucket_name, Key=full_key)
            storage_class = response.get('StorageClass', 'STANDARD')
            restore_header = response.get('Restore', '')
            
            if not restore_header:
                return {'storage_class': storage_class, 'restore_status': 'not_started'}
            elif 'ongoing-request="true"' in restore_header:
                return {'storage_class': storage_class, 'restore_status': 'in_progress'}
            elif 'ongoing-request="false"' in restore_header:
                return {'storage_class': storage_class, 'restore_status': 'completed'}
            else:
                return {'storage_class': storage_class, 'restore_status': 'unknown'}
        except ClientError as e:
            return {'storage_class': 'ERROR', 'restore_status': 'error', 'error': str(e)}

    def move_objects(self, moves: List[tuple]) -> int:
        """
        Move multiple objects using server-side copy + delete.
        
        Args:
            moves: List of (old_key, new_key) tuples.
            
        Returns:
            Number of successfully moved objects.
        """
        success_count = 0
        for old_key, new_key in moves:
            if self.move_object(old_key, new_key):
                success_count += 1
        return success_count

    def object_exists(self, key: str) -> bool:
        """Check if an object exists in S3."""
        full_key = self._full_key(key)
        try:
            self.client.head_object(Bucket=self.bucket_name, Key=full_key)
            return True
        except ClientError:
            return False
