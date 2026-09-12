# yt-dlp Download Persistence Design

## Current risk

`YtdlpDownloadEngine` keeps its queue in an in-memory `StateFlow`, and `YtdlpDownloadService` returns `START_NOT_STICKY`. Android process death can therefore discard queued or active jobs without a durable record.

## Proposed model

Introduce a dedicated Room `YtdlpDownloadJobEntity` rather than overloading `DownloadItemEntity`, because yt-dlp jobs have subprocess-specific state, command arguments, output discovery, and retry semantics. Suggested fields are `id`, `sourceUrl`, `title`, `directory`, `requestedFormat`, `status`, `progressPercent`, `detail`, `outputPath`, `failureReason`, `createdAt`, `updatedAt`, `startedAt`, `finishedAt`, and a cancellation marker. Persist only non-sensitive metadata; cookies and credentials must remain in the existing protected/runtime paths.

The engine should become a worker over the repository: claim one queued job, transition it to `RUNNING`, update progress transactionally, and finish it as `COMPLETED`, `FAILED`, or `CANCELLED`. On startup, jobs left in `RUNNING` should be moved to `QUEUED` or `FAILED` according to an explicit recovery policy. The foreground service should drain the repository rather than an in-memory list and should use `START_REDELIVER_INTENT` only after idempotent job claiming is implemented.

## Required implementation sequence

1. Add the entity, DAO, schema migration, and migration tests.
2. Add repository transitions with compare-and-set semantics so two workers cannot claim the same job.
3. Adapt `YtdlpDownloadEngine` behind the repository while preserving its current subprocess and output-discovery logic.
4. Recover interrupted jobs on application/service startup.
5. Add process-death, retry, cancellation, and duplicate-claim instrumentation tests.

This document is intentionally design-only. No yt-dlp runtime behavior is changed by this stabilization batch.
