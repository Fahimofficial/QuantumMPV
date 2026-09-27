package com.quantummpv.app.presentation.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import com.quantummpv.app.BuildConfig
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.developer.crashx.CrashActivity as CrashX

internal object CrashReportStore {
  private const val TAG = "CrashReportStore"
  private val handlingCrash = AtomicBoolean(false)

  fun install(context: Context) {
    val application = context.applicationContext
    val crashHandler = Thread.getDefaultUncaughtExceptionHandler() ?: return
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      if (handlingCrash.compareAndSet(false, true)) {
        try {
          capture(application, thread, throwable)
        } catch (captureError: Throwable) {
          Log.e(TAG, "Unable to save the full crash report", captureError)
        } finally {
          crashHandler.uncaughtException(thread, throwable)
        }
      }
    }
  }

  private fun capture(
    context: Context,
    thread: Thread,
    throwable: Throwable,
  ) {
    val directory = reportDirectory(context)
    val metadata = AtomicFile(File(directory, "metadata.json"))
    metadata.delete()
    val reportId = UUID.randomUUID().toString()
    writeAtomically(File(directory, "exception.txt")) { writer ->
      writer.appendLine("mpvRx crash report")
      writer.appendLine("Report ID: $reportId")
      writer.appendLine("Captured: ${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).format(Date())}")
      writer.appendLine("Process ID: ${Process.myPid()}")
      writer.appendLine("Device uptime: ${SystemClock.elapsedRealtime()} ms since boot")
      writer.appendLine("Crash thread: ${thread.name} (id=${thread.id}, state=${thread.state})")
      writer.appendLine()
      writer.appendLine(deviceInfo())
      writer.appendLine()
      writer.appendLine("=== Full exception, causes and suppressed exceptions ===")
      writeThrowable(writer, throwable)
      writer.appendLine()
      writer.appendLine("=== All thread stacks at crash ===")
      try {
        Thread.getAllStackTraces().entries.sortedBy { it.key.name }.forEach { (currentThread, stack) ->
          writer.appendLine(
            "${currentThread.name} (id=${currentThread.id}, state=${currentThread.state}, " +
              "daemon=${currentThread.isDaemon}, priority=${currentThread.priority})",
          )
          stack.forEach { writer.appendLine("    at $it") }
          writer.appendLine()
        }
      } catch (error: Exception) {
        writer.appendLine("Thread snapshot unavailable: $error")
      }
    }
    writeMetadata(
      directory,
      JSONObject()
        .put("reportId", reportId)
        .put("processId", Process.myPid())
        .put("throwableClass", throwable.javaClass.name)
        .put("completed", false),
    )
  }

  private fun writeThrowable(
    writer: BufferedWriter,
    throwable: Throwable,
  ) {
    val visited = IdentityHashMap<Throwable, Boolean>()
    val pending = ArrayDeque<Pair<String, Throwable>>()
    pending.addLast("Exception" to throwable)
    while (pending.isNotEmpty()) {
      val (relationship, current) = pending.removeLast()
      writer.appendLine("$relationship: $current")
      if (visited.put(current, true) != null) {
        writer.appendLine("    Circular or previously printed exception reference")
        continue
      }
      current.stackTrace.forEach { writer.appendLine("    at $it") }
      current.cause?.let { pending.addLast("Caused by" to it) }
      current.suppressed.reversed().forEach { pending.addLast("Suppressed by ${current.javaClass.name}" to it) }
    }
  }

  @Synchronized
  fun complete(
    context: Context,
    intent: Intent,
  ): File {
    val directory = reportDirectory(context)
    val crashId = CrashX.getCrashIdFromIntent(intent)
    val metadata =
      runCatching {
        AtomicFile(File(directory, "metadata.json")).openRead().bufferedReader().use { JSONObject(it.readText()) }
      }.getOrNull()
    val reportId =
      runCatching { UUID.fromString(metadata?.optString("reportId")) }.getOrNull() ?: UUID.randomUUID()
    val report = File(directory, "mpvrx-crash-$reportId.txt")
    if (
      metadata != null && metadata.optString("crashId") == crashId &&
      metadata.optBoolean("completed") && report.isFile
    ) {
      return report
    }
    val hasFullException =
      metadata != null && !metadata.optBoolean("completed") &&
        metadata.optString("throwableClass") == CrashX.getThrowableClassFromIntent(intent) &&
        File(directory, "exception.txt").isFile
    writeAtomically(report) { writer ->
      if (hasFullException) {
        AtomicFile(File(directory, "exception.txt")).openRead().bufferedReader().use { it.copyTo(writer) }
      } else {
        writer.appendLine("Full on-disk exception capture was unavailable. CrashX fallback details follow.")
        writer.appendLine(CrashX.getAllErrorDetailsFromIntent(context, intent))
      }
      writer.appendLine()
      writer.appendLine("=== Recovery metadata ===")
      writer.appendLine("CrashX ID: $crashId")
      writer.appendLine("Crash date: ${CrashX.getCrashDateFromIntent(intent)}")
      writer.appendLine("Crash thread: ${CrashX.getThreadNameFromIntent(intent)}")
      writer.appendLine()
      writer.appendLine("=== Recent activity lifecycle ===")
      writer.appendLine(CrashX.getActivityLogFromIntent(intent).orEmpty())
      writer.appendLine()
      writer.appendLine("=== Available logcat buffers ===")
      val crashedProcessId = metadata?.optInt("processId", -1)?.takeIf { hasFullException && it > 0 }
      val arguments =
        mutableListOf("logcat", "-b", "main", "-b", "system", "-b", "crash", "-d", "-v", "threadtime")
      if (crashedProcessId != null) arguments += "--pid=$crashedProcessId"
      try {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        try {
          process.inputStream.bufferedReader().use { it.copyTo(writer) }
          val exitCode = process.waitFor()
          if (exitCode != 0) writer.appendLine("Logcat exited with status $exitCode")
        } finally {
          process.destroy()
        }
      } catch (error: Exception) {
        writer.appendLine("Logcat unavailable: $error")
      }
    }
    writeMetadata(
      directory,
      (metadata ?: JSONObject()).put("reportId", reportId.toString()).put("crashId", crashId).put("completed", true),
    )
    directory.listFiles { file -> file.name.startsWith("mpvrx-crash-") && file.extension == "txt" }
      ?.sortedByDescending(File::lastModified)?.drop(5)?.forEach { it.delete() }
    return report
  }

  fun deviceInfo(): String =
    buildString {
      appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_SHA})")
      appendLine("Package: ${BuildConfig.APPLICATION_ID}")
      appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
      appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
      appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
      appendLine("Brand: ${Build.BRAND}")
      appendLine("Hardware: ${Build.HARDWARE}")
      appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
      appendLine("Build fingerprint: ${Build.FINGERPRINT}")
      appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
      val runtime = Runtime.getRuntime()
      appendLine("Heap bytes: used=${runtime.totalMemory() - runtime.freeMemory()}, max=${runtime.maxMemory()}")
      appendLine("Native heap bytes: ${android.os.Debug.getNativeHeapAllocatedSize()}")
    }

  private fun reportDirectory(context: Context): File =
    File(context.noBackupFilesDir, "crash-reports").apply {
      check(isDirectory || mkdirs()) { "Unable to create crash report directory" }
    }

  private fun writeMetadata(
    directory: File,
    metadata: JSONObject,
  ) = writeAtomically(File(directory, "metadata.json")) { it.write(metadata.toString()) }

  private fun writeAtomically(
    file: File,
    write: (BufferedWriter) -> Unit,
  ) {
    val atomicFile = AtomicFile(file)
    val output = atomicFile.startWrite()
    try {
      val writer = output.bufferedWriter(Charsets.UTF_8)
      write(writer)
      writer.flush()
      atomicFile.finishWrite(output)
    } catch (error: Throwable) {
      atomicFile.failWrite(output)
      throw error
    }
  }
}