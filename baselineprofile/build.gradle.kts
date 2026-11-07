import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.androidx.baselineprofile)
}

android {
  namespace = "com.quantummpv.baselineprofile"
  compileSdk = 37

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Profiles and benchmarks target the main distribution only.
  flavorDimensions += "distribution"
  productFlavors {
    create("standard") { dimension = "distribution" }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  targetProjectPath = ":app"
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

baselineProfile {
  // A connected device (API 28+, ideally a mid-range phone with videos on it) runs the journeys.
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
