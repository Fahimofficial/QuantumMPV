with open("app/build.gradle.kts", "r") as f:
    content = f.read()
if "crashx" not in content:
    content = content.replace("implementation(libs.androidx.core.ktx)", "implementation(libs.androidx.core.ktx)\n  implementation(libs.crashx)\n  implementation(libs.kmp.vibrate)")
with open("app/build.gradle.kts", "w") as f:
    f.write(content)

with open("gradle/libs.versions.toml", "r") as f:
    content = f.read()
if "crashx" not in content:
    content = content.replace('mediainfo = "v1.0.0-fix"', 'mediainfo = "v1.0.0-fix"\ncrashx = "7.0.1"\nkmpVibrate = "1.1.0"')
    content = content.replace('leakcanary-android = { module = "com.squareup.leakcanary:leakcanary-android", version.ref = "leakcanary" }', 'leakcanary-android = { module = "com.squareup.leakcanary:leakcanary-android", version.ref = "leakcanary" }\ncrashx = { module = "io.github.tutorialsandroid:crashx", version.ref = "crashx" }\nkmp-vibrate = { module = "io.github.jmseb3:vibrate", version.ref = "kmpVibrate" }')
with open("gradle/libs.versions.toml", "w") as f:
    f.write(content)
