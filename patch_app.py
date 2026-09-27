with open("app/src/main/java/com/quantummpv/app/App.kt", "r") as f:
    content = f.read()

if "CrashConfig.Builder" not in content:
    import_crash = "import com.developer.crashx.config.CrashConfig\nimport com.quantummpv.app.presentation.crash.CrashReportStore"
    content = content.replace("import com.quantummpv.app.presentation.crash.CrashActivity", f"import com.quantummpv.app.presentation.crash.CrashActivity\n{import_crash}")
    
    init_crash = """    val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
"""
    content = content.replace("configureDebugStrictMode()", f"{init_crash}\n    configureDebugStrictMode()")
    content = content.replace("import android.app.Activity", "import android.app.Activity\nimport android.app.ActivityManager")
    content = content.replace("Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))", "")

with open("app/src/main/java/com/quantummpv/app/App.kt", "w") as f:
    f.write(content)
