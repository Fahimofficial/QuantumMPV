package com.quantummpv.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateIntegrityTest {
  private val hash = "9e9fb516e3b08a2d6efc136d103eac2d4c4300dda0baf9cff0dfd9a0764f0cdf"
  private val name = "QuantumMPV-arm64-v8a-v1.3.0.apk"

  private fun asset(
    digest: String? = null,
    sha256: String? = null,
  ) = Asset(downloadUrl = "https://example.com/$name", name = name, size = 1, contentType = "apk", digest = digest, sha256 = sha256)

  @Test
  fun prefersPreviewFeedHashThenGithubDigest() {
    assertEquals(hash, UpdateIntegrity.expectedSha256(asset(sha256 = hash.uppercase(), digest = "sha256:" + "0".repeat(64)), ""))
    assertEquals(hash, UpdateIntegrity.expectedSha256(asset(digest = "sha256:$hash"), ""))
  }

  @Test
  fun fallsBackToChecksumBlockInReleaseNotes() {
    val body =
      """
      ### SHA-256 checksums

      ```text
      ${"1".repeat(64)}  release-assets/QuantumMPV-universal-v1.3.0.apk
      $hash *release-assets/$name
      ```
      """.trimIndent()

    assertEquals(hash, UpdateIntegrity.expectedSha256(asset(), body))
  }

  @Test
  fun returnsNullWhenNoTrustworthyHashExists() {
    assertNull(UpdateIntegrity.expectedSha256(asset(digest = "md5:abc"), "no checksums here"))
    assertNull(UpdateIntegrity.expectedSha256(asset(sha256 = "not-a-hash"), "$hash  other.apk"))
  }
}
