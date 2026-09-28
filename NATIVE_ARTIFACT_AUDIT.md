
## Follow-up dependency remediation

After the release hardening commit, GitHub reported 19 Dependabot findings. The Dependabot alert endpoint itself was unavailable to the configured GitHub token, so the repository SBOM and OSV advisory database were used to identify the package/version pairs without guessing.

The vulnerable build-tool copies were traced to Android Gradle Plugin 9.3.2, whose `builder` and `apkzlib` POMs require Bouncy Castle 1.79. AGP 9.4.1 changes those dependencies to 1.80.2. The application security constraints were upgraded to Bouncy Castle 1.85 for `bcprov-jdk18on`, `bcpkix-jdk18on`, and `bcutil-jdk18on`. OSV reports no matching advisories for the selected Bouncy Castle 1.85 artifacts or the already-constrained Commons Lang 3.18.0, jose4j 0.9.6, and JDOM 2.0.6.1 versions.

The dependency remediation is committed separately after the native pipeline fix. A full Android build remains dependent on an Android SDK-equipped runner; the sandbox still lacks `ANDROID_HOME`.
