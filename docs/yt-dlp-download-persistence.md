# yt-dlp Download Persistence Audit

The current QuantumMPV source contains `YtdlpDownloadEngine` and `YtdlpDownloadService`. The engine keeps `Job` objects in an in-memory `MutableStateFlow`, assigns IDs from an in-memory counter, and the service returns `START_NOT_STICKY`. Consequently, queued, running, and completed yt-dlp jobs can be lost when the Android process is killed. This is a real reliability gap.

## Recommended implementation

Introduce a dedicated Room `YtdlpDownloadJobEntity` and DAO rather than reusing `DownloadItemEntity`. Persist `sourceUrl`, title, destination directory, state, progress, detail, output path, error, and timestamps. Keep cookies and credentials out of the database. Change enqueue/retry/cancel/update operations to update the repository, use an atomic compare-and-set claim for the next queued job, and reload persisted jobs when the engine is constructed. On startup, recover jobs left in `RUNNING` according to an explicit policy, normally returning them to `QUEUED` if their partial output can safely be resumed or marking them `FAILED` otherwise. Change the foreground service to redeliver the start intent only after the queue is repository-backed and idempotent.

## Current status

This batch does not implement the Room-backed yt-dlp queue because it requires a new schema version, engine API changes, process-recovery behavior, and dedicated tests. The migration and CI improvements are implemented separately. The next focused batch should implement this design end-to-end rather than introducing a partial persistence layer that could duplicate or lose downloads.
