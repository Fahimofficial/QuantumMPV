// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose.compiler) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.room) apply false
  alias(libs.plugins.ktlint) apply false
}

allprojects {
  configurations.configureEach {
    resolutionStrategy.eachDependency {
      when {
        requested.group == "io.netty" -> useVersion("4.1.137.Final")
        requested.group == "org.bouncycastle" -> useVersion("1.84")
        requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> useVersion("3.18.0")
        requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> useVersion("4.5.14")
        requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> useVersion("0.9.6")
        requested.group == "org.jdom" && requested.name == "jdom2" -> useVersion("2.0.6.1")
        requested.group == "ch.qos.logback" -> useVersion("1.5.34")
      }
    }
  }

  pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
      baseline.set(file("${rootProject.projectDir}/ktlint-baseline.xml"))
    }
  }
}
