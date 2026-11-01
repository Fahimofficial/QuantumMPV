# Release procedure

## Versioning policy

- QuantumMPV uses its own semantic versions on the **1.x** line (`1.MINOR.PATCH`).
  Do not reuse upstream mpvRx version numbers.
- Stable tags: `v1.MINOR.PATCH`. Preview tags: `v1.MINOR.PATCH-preview.N`.
- `versionName` and `releaseVersionCode` in `app/build.gradle.kts` are set by
  `tools/prepare_release.py`. `releaseVersionCode = major*10000 + minor*100 + patch`.
- Android `versionCode` bands guarantee upgrades. A stable release uses
  `releaseVersionCode * 10000 + 9999`. Previews use the next band plus the commit
  count. Each APK multiplies by 10 and adds an ABI code (universal 0,
  armeabi-v7a 1, arm64-v8a 2, x86 3, x86_64 4). The release workflow verifies the
  exact `versionCode` of every APK.
- Preview and stable builds share the application ID `com.quantummpv.app` and
  must be signed with the **same key**. Otherwise users cannot move between
  channels or update.

## Required secrets and variables

| Name | Type | Used by | Notes |
| --- | --- | --- | --- |
| `SIGNING_KEYSTORE` | secret | `release.yml` (sign job), `pre-release.yml`, `preview.yml` | Base64 of the `.jks`/`.keystore` file (`base64 -w0 release.jks`) |
| `SIGNING_KEY_ALIAS` | secret | same | Key alias |
| `SIGNING_STORE_PASSWORD` | secret | same | Keystore password |
| `KEY_PASSWORD` | secret | same | Key password |
| `RELEASE_SIGNING_CERT_SHA256` | repository **variable** (not secret) | `release.yml` | SHA-256 of the signing certificate, for example from `apksigner verify --print-certs QuantumMPV-universal-v1.4.0.apk`. Colons, spaces and letter case are ignored, so the `keytool` form also works. Required: stable releases fail before signing when it is unset, and a release signed with any other key fails before publishing. |

Store the four signing secrets as **environment secrets** of `production` (stable)
and `preview` (preview builds and tagged previews). Then delete the
repository-level copies. Environment secrets are only released to jobs that
reference the environment and pass its protection rules.

Environment protection (Settings → Environments):

| Environment | Deployment branches and tags | Reviewers |
| --- | --- | --- |
| `production` | Selected tags: `v1.*.*` only | Required reviewer: the maintainer |
| `preview` | Selected branches and tags: branch `master`, tags `v*-preview.*` | None (every master push builds a preview) |

Stable and tagged preview releases (`release.yml`, `pre-release.yml`) run on a
tag ref, including runs started by Prepare Release and manual runs, which must
use **Use workflow from → Tags**. A run started from a branch therefore cannot
obtain the production secrets. The untagged preview build (`preview.yml`) is the
exception: it runs on pushes to `master` and uses only the `preview`
environment, whose policy allows branch `master`.

The workflows never print these values. A temporary keystore is created with
`umask 077` in `$RUNNER_TEMP` and deleted on exit. Keystores must never be
committed: `tools/check_android_security.py` fails if a `.jks`, `.keystore`,
`.p12` or `.pfx` file is tracked.

## Cutting a stable release

1. Open a PR that:
   - runs `python3 tools/prepare_release.py X.Y.Z` (updates `versionName` and
     `releaseVersionCode`), and
   - renames the `## Unreleased` section of `CHANGELOG.md` to `## X.Y.Z`.
2. Merge it after all required checks pass. Wait for the master run of
   `Android CI`, `Static analysis`, `Secret scan`, `Security analysis` and `CodeQL`
   on the merge commit to finish.
3. Start **Actions → Prepare Release → Run workflow** with `version = X.Y.Z` and
   `prerelease = false`. It:
   - checks that master is prepared for `X.Y.Z`;
   - checks the required checks on the master commit;
   - creates the annotated tag `vX.Y.Z` through the API;
   - dispatches **Make release** on that tag.

   If the dispatch fails after the tag was created, do not delete the tag:
   start **Make release** manually with **Use workflow from → Tags → vX.Y.Z**.

   Alternatively, tag the merge commit yourself and push the tag:
   `git tag -a vX.Y.Z -m "Release vX.Y.Z" <sha> && git push origin vX.Y.Z`.
4. **Make release** runs `resolve → instrumented + build → sign → publish`
   (see [CI_CD.md](CI_CD.md)). If the `production` environment has required
   reviewers, approve the `sign` job when prompted.

The release fails safely, and nothing is published, if any of these is true:

- the tag is not `v1.X.Y`;
- the tag is not on master;
- the version or changelog does not match the tag;
- a required check is missing or failed;
- an instrumented test fails;
- an APK is missing, debuggable or has an unexpected `versionCode`;
- a signing secret is missing;
- the APKs are not all signed with the same certificate;
- `RELEASE_SIGNING_CERT_SHA256` is unset or not a SHA-256 digest (checked
  before anything is signed);
- the signing certificate differs from `RELEASE_SIGNING_CERT_SHA256`.

## Cutting a preview release

Use **Prepare Release** with `prerelease = true`, or push a
`vX.Y.Z-preview.N` tag on a master commit. **Make Pre Release** checks the tag
format, that the commit is on master and that its required checks passed, then
builds, signs and publishes a GitHub pre-release.

Untagged preview builds are produced automatically by **Preview Build** for app
changes on master and published to GitHub Pages for the in-app preview channel.

## Verifying a published release

```bash
# 1. Checksums
sha256sum -c QuantumMPV-vX.Y.Z-SHA256SUMS.txt

# 2. Build provenance (GitHub CLI 2.49+)
gh attestation verify QuantumMPV-arm64-v8a-vX.Y.Z.apk --repo Fahimofficial/QuantumMPV

# 3. Signature and signing certificate
apksigner verify --print-certs QuantumMPV-arm64-v8a-vX.Y.Z.apk
#    The "Signer #1 certificate SHA-256 digest" must equal RELEASE_SIGNING_CERT_SHA256.

# 4. Not debuggable, expected version
aapt dump badging QuantumMPV-arm64-v8a-vX.Y.Z.apk | grep -E "^package:|application-debuggable"
```

`QuantumMPV-vX.Y.Z-sbom.spdx.json` is the SPDX SBOM exported from the GitHub
dependency graph. It covers Gradle dependencies submitted by
`dependency-submission.yml` from master at publication time. The native mpv/FFmpeg
AARs are pinned by commit and SHA-256 in `native-artifacts.json`.

## Signing key compromise or loss

- **Compromise:** rotate the environment secrets immediately. Android cannot
  replace an app signing key in place for sideloaded APKs, except through APK
  Signature Scheme v3 key rotation (`apksigner rotate`), which needs the old key.
  Publish an advisory through the Security tab and tell users how to verify
  certificates.
- **Loss:** existing installations cannot be updated. Users must uninstall and
  reinstall. Keep an offline, encrypted backup of the keystore.
