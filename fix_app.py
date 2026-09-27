with open("app/src/main/java/com/quantummpv/app/App.kt", "r") as f:
    content = f.read()

bad_string = """  private fun     val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Application.getProcessName()
    } else {
      getSystemService(ActivityManager::class.java).runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
    }
    if (processName == "$packageName:crash" || processName == "$packageName:crashx_error") {
      org.koin.core.context.startKoin {
        androidContext(this@App)
        modules(PreferencesModule)
      }
      return
    }

    CrashConfig.Builder.create()
      .enabled(true)
      .errorActivity(CrashActivity::class.java)
      .restartActivity(MainActivity::class.java)
      .backgroundMode(CrashConfig.BACKGROUND_MODE_SHOW_CUSTOM)
      .minTimeBetweenCrashesMs(5_000)
      .maxStackTraceSize(96 * 1024)
      .trackActivities(true)
      .maxActivityLogEntries(32)
      .showErrorDetails(true)
      .showReportButton(true)
      .showCloseButton(true)
      .logErrorOnRestart(false)
      .includeStackTrace(true)
      .includeBuildDate(false)
      .crashIdPrefix("MPVRX")
      .apply()
    CrashReportStore.install(this)

    configureDebugStrictMode()"""

content = content.replace(bad_string, "  private fun configureDebugStrictMode()")

with open("app/src/main/java/com/quantummpv/app/App.kt", "w") as f:
    f.write(content)
