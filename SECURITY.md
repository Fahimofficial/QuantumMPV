# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| Latest stable release | Yes |
| Latest `v*-preview.*` build | Yes (best effort) |
| Older releases | No |

QuantumMPV is an Android application built on libmpv/FFmpeg. Security fixes land
on `master` first and ship in the next preview build, then in the next stable
release.

## Reporting a vulnerability

Please do **not** open a public issue for security problems.

- Preferred: open a private report via GitHub Security Advisories
  (Security tab -> "Report a vulnerability").
- Alternative: contact the maintainer through the channels listed in
  [SUPPORT.md](SUPPORT.md) and ask for a private channel before sharing details.

Please include:

- affected version (app version and ABI, e.g. `v1.1.0-preview.12`, arm64-v8a)
- Android version and device
- steps to reproduce, and a sample file or URL if the issue is media-specific
- impact assessment and, if known, the affected component (playback engine,
  subtitle handling, file access, Cast, intent handling, shaders)

## What to expect

- Acknowledgement within 7 days.
- An assessment and planned fix window within 30 days.
- Credit in the release notes once a fix ships, unless you prefer to stay
  anonymous.

## Scope

In scope: the QuantumMPV app code in this repository, its build and release
workflows, and the metadata it publishes.

Out of scope: vulnerabilities in upstream projects (mpv, libmpv, FFmpeg,
mpv-android, mpvKt/mpvEx/mpvRx) that QuantumMPV only consumes — report those
upstream. We will still help coordinate if a fix requires changes here.
