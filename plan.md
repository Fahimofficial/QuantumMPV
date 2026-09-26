We need to create a complete media indexing pipeline.
Steps:
1. Create `MediaIndexEntity` with stable URI, display name, folder, etc.
2. Create `MediaIndexDao` with FTS (Full Text Search) capabilities.
3. Create `MediaScannerService` or WorkManager worker that incrementally scans configured roots.
4. Update `MediaFileRepository` to use `MediaIndexDao` instead of runtime `FolderViewScanner`.
5. Implement FastScroll in the Compose UI.
