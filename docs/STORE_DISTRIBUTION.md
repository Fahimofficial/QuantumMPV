# Store distribution

## Google Play

The stable `.github/workflows/release.yml` pipeline builds and signs the Standard-flavor App Bundle as `QuantumMPV-standard-vX.Y.Z.aab`. It uses the same release certificate as the APK variants and verifies that the certificates match. FongMi and No-Vulkan APK variants remain available for direct distribution; the Play listing uses the Standard bundle.

One-time setup:

1. Create the Play Console app with package name `com.quantummpv.app`. Complete the initial app setup and make the first manual upload if Play Console requires it.
2. Enable the Google Play Android Developer API in the Google Cloud project.
3. Configure Workload Identity Federation for GitHub Actions. Restrict the provider's attribute condition to this repository and the `play-store` GitHub environment. Do not create a downloadable service-account JSON key.
4. Give the Google service account only the Play Console app permissions needed to manage releases for this app. Add it under Play Console API access.
5. Create a GitHub environment named `play-store`, restrict deployments to the default branch, and add required reviewers. Add these environment variables:
   - `PLAY_WIF_PROVIDER`: the full Workload Identity Provider resource name.
   - `PLAY_SERVICE_ACCOUNT`: the service-account email address.
6. Publish the GitHub release, then run **Actions → Publish to Google Play** with that tag. Internal, alpha and beta tracks are completed automatically after the environment approval. Production is uploaded as a draft and must be reviewed and published manually in Play Console.

The release's `production` environment still protects the private signing key. The Play upload workflow receives only the public AAB downloaded from the published GitHub release and short-lived OIDC credentials.

## F-Droid status

QuantumMPV is not ready for the official F-Droid repository yet. The current build includes Google Cast framework integration and consumes prebuilt native mpv AARs from `app/libs`; additional native artifacts are downloaded by `tools/verify_native_artifacts.py`. F-Droid builds must be reproducible from public source and cannot rely on opaque prebuilt binaries or downloading dependencies during the build.

A compliant F-Droid variant will need a no-Google-Play-Services build (including a Cast-free path) and an offline build recipe that compiles the required native code from its licensed source. Only after that source and licensing audit should an F-Droid metadata package be submitted for review. GitHub Actions cannot directly publish an app into the official F-Droid catalogue.
