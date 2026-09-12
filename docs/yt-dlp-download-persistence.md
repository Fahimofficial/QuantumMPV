# yt-dlp Download Persistence

QuantumMPV now persists yt-dlp download jobs in Room through the `ytdlp_download_jobs` table. The engine restores jobs when constructed, requeues jobs that were interrupted in `RUNNING`, and keeps queue state, progress, errors, and output paths durable across process restarts.

Queue claims use a compare-and-set update so only a queued job can transition to `RUNNING`. Enqueue, retry, cancellation, progress, completion, and failure updates are written to the repository. The foreground service uses `START_REDELIVER_INTENT`, allowing Android to redeliver the service start request after process recreation. Cookies and credentials remain outside the database.

The database schema was incremented from version 21 to 22 with `MIGRATION_21_22`. Instrumentation coverage verifies the new table and queued-job fields, while the existing migration tests continue to verify the 19-to-21 upgrade paths and Navidrome defaults.

A future refinement could move persistence writes behind a dedicated repository and add explicit resumable-partial-file policy. The current implementation safely requeues interrupted jobs and lets yt-dlp resolve the existing output/partial-file behavior on retry.
