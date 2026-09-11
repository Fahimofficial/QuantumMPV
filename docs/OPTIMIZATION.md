# QuantumMPV optimization guide

## Release shrinking

The release build already enables the Android shrinkers in `app/build.gradle.kts`:

```kotlin
buildTypes {
  named("release") {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
      getDefaultProguardFile("proguard-android-optimize.txt"),
      "proguard-rules.pro",
    )
  }
}
```

`preview` inherits this release configuration. Do not enable shrinking for debug builds while diagnosing playback or JNI issues.

## Safe audit command

From the repository root, run:

```bash
chmod +x tools/analyze_dead_code.sh
tools/analyze_dead_code.sh
```

The script runs release lint, dependency reporting, a release APK build, and R8 mapping generation. It writes report copies under `app/build/reports/optimization/`. It is intentionally report-only and never deletes code or resources.

Android Studio provides the same checks through **Analyze > Inspect Code**, with the inspection scope set to the `app` module. For resources, run **Refactor > Remove Unused Resources** only after reviewing the preview list. For Gradle dependency candidates, use the Gradle `dependencies` report and confirm each dependency is not required by flavor-specific source sets, JNI, reflection, XML, or generated code.

## Common dead-code locations in an mpv-android fork

The highest-risk cleanup areas are duplicated fork-specific activities and fragments, legacy XML preference screens superseded by Compose screens, old network providers, unused subtitle and lyrics adapters, duplicate media-info renderers, experimental visualizers, abandoned Cast and MediaSession bridges, old update clients, flavor-specific wrappers that are no longer referenced, and bundled editor languages or shader packs that the product no longer exposes.

Native code and assets require extra care. Inspect `app/src/main/cpp`, `app/src/main/jniLibs`, `app/src/main/assets`, JNI method names, `AndroidManifest.xml`, Room entities and migrations, Koin modules, serialization registrations, notification actions, and custom XML attributes before removal. A class with no ordinary Kotlin reference can still be loaded by the manifest, reflection, JNI, or a generated adapter.

## Feature-port rule

A sister-project feature should be ported only after identifying its exact source commit and the matching QuantumMPV integration point. The shared libmpv core does not guarantee compatible preference keys, event names, JNI wrappers, Compose state models, or database schemas. A concrete port needs the feature name or upstream link, its UI entry point, and whether it should be enabled by default.
