package com.quantummpv.app.data.network.client

import com.jcraft.jsch.HostKeyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SftpHostKeyRepositoryTest {
  private class MemoryKnownHosts : SftpKnownHosts {
    val entries = mutableMapOf<String, String>()

    override fun trustedKey(hostPort: String) = entries[hostPort]

    override fun trust(
      hostPort: String,
      key: String,
    ) {
      entries[hostPort] = key
    }

    override fun forget(hostPort: String) {
      entries.remove(hostPort)
    }
  }

  private val serverKey = byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".toByteArray() + ByteArray(32) { it.toByte() }
  private val otherKey = byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".toByteArray() + ByteArray(32) { (it * 3).toByte() }
  private val hostPort = SftpHostKeyFormat.hostPort("NAS.local", 22)

  @Test
  fun unknownServerIsRejectedAndItsKeyIsOfferedForConfirmation() {
    val knownHosts = MemoryKnownHosts()
    val repository = StrictSftpHostKeyRepository(hostPort, knownHosts)

    assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("NAS.local", serverKey))

    val challenge = repository.rejection!!
    assertEquals("nas.local:22", challenge.hostPort)
    assertFalse(challenge.isChanged)
    assertEquals(Base64.getEncoder().encodeToString(serverKey), challenge.presentedKey)
    assertTrue(challenge.fingerprint.startsWith("SHA256:"))
    assertTrue("nothing is trusted automatically", knownHosts.entries.isEmpty())
  }

  @Test
  fun confirmedKeyIsAcceptedOnTheNextConnection() {
    val knownHosts = MemoryKnownHosts()
    val first = StrictSftpHostKeyRepository(hostPort, knownHosts)
    first.check("NAS.local", serverKey)
    val challenge = first.rejection!!
    knownHosts.trust(challenge.hostPort, challenge.presentedKey)

    val second = StrictSftpHostKeyRepository(hostPort, knownHosts)
    assertEquals(HostKeyRepository.OK, second.check("NAS.local", serverKey))
    assertNull(second.rejection)
  }

  @Test
  fun aDifferentKeyIsReportedAsChangedWithBothFingerprints() {
    val knownHosts = MemoryKnownHosts()
    knownHosts.trust(hostPort, SftpHostKeyFormat.encode(serverKey))
    val repository = StrictSftpHostKeyRepository(hostPort, knownHosts)

    assertEquals(HostKeyRepository.CHANGED, repository.check("NAS.local", otherKey))

    val challenge = repository.rejection!!
    assertTrue(challenge.isChanged)
    assertEquals(SftpHostKeyFormat.fingerprint(serverKey), challenge.previousFingerprint)
    assertEquals(SftpHostKeyFormat.fingerprint(otherKey), challenge.fingerprint)
    assertEquals("the trusted key is not replaced silently", SftpHostKeyFormat.encode(serverKey), knownHosts.trustedKey(hostPort))
  }

  @Test
  fun jschCannotAddKeysBehindTheUsersBack() {
    val knownHosts = MemoryKnownHosts()
    val repository = StrictSftpHostKeyRepository(hostPort, knownHosts)
    repository.add(null, null)
    assertTrue(knownHosts.entries.isEmpty())
  }

  @Test
  fun fingerprintMatchesOpenSshFormat() {
    // `ssh-keygen -lf` prints SHA256 digests as unpadded base64.
    val fingerprint = SftpHostKeyFormat.fingerprint(serverKey)
    assertFalse(fingerprint.endsWith("="))
    assertEquals(7 + 43, fingerprint.length)
  }
}
