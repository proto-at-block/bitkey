package build.wallet.encryption

import android.app.Application
import androidx.test.core.app.ApplicationProvider.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import build.wallet.crypto.SSBLocalWrappingPublicKeys
import build.wallet.crypto.SSBServerBundle
import build.wallet.crypto.SelfSovereignBackupImpl
import build.wallet.encrypt.HkdfImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.secureenclave.*
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

val serverPrivateKey = "I'm the server private key"
  .toByteArray()
  .toByteString() // Variable placed here for visibility in MockServer and the test.

@RunWith(AndroidJUnit4::class)
class SelfSovereignBackupInstrumentedTest {
  private class MockServer(
    val se: SecureEnclaveImpl,
    val hkdf: HkdfImpl,
    val symmetricKeyEncryptor: SymmetricKeyEncryptorImpl,
  ) {
    fun encrypt(wrappingPublicKeys: SSBLocalWrappingPublicKeys): SSBServerBundle {
      val eph: SeKeyPair = se.generateP256KeyPair(
        SeKeySpec(
          name = "key-testDiffieHellman-server",
          purposes = SeKeyPurposes.of(SeKeyPurpose.AGREEMENT),
          usageConstraints = SeKeyUsageConstraints.NONE,
          validity = null
        )
      )

      // First ECDH, with lka
      val s1 = se.diffieHellman(eph.privateKey, wrappingPublicKeys.lkaPub)

      // Second ECDH, with lkn
      val s2 = se.diffieHellman(eph.privateKey, wrappingPublicKeys.lknPub)

      val key = hkdf.deriveKey(
        ikm = (s1 + s2).toByteString(),
        null, null, 32
      )

      val wrappedKey = symmetricKeyEncryptor.sealNoMetadata(
        unsealedData = serverPrivateKey,
        key = key
      )

      return SSBServerBundle(
        ephemeralPublicKey = eph.publicKey.bytes.toByteString(),
        sealedServerKeyShare = wrappedKey
      )
    }
  }

  @Test
  fun testSsb() {
    val application = getApplicationContext<Application>()
    val se = SecureEnclaveImpl(application)
    val hkdf = HkdfImpl()
    val symmetricKeyEncryptor = SymmetricKeyEncryptorImpl()
    val ssb = SelfSovereignBackupImpl(
      se,
      symmetricKeyEncryptor,
      hkdf,
      skipAuthenticationForKeysInTestOnly = true
    )

    val keys = ssb.generateLocalWrappingKeys()
    val keys2 = ssb.exportLocalWrappingPublicKeys()
    assertTrue(keys.lkaPub.bytes.contentEquals(keys2.lkaPub.bytes))
    assertTrue(keys.lknPub.bytes.contentEquals(keys2.lknPub.bytes))

    val mockServer = MockServer(se, hkdf, symmetricKeyEncryptor)
    val bundle = mockServer.encrypt(keys)
    val ptServerKey = ssb.decryptServerKeyShare(bundle)

    assertEquals(serverPrivateKey, ptServerKey.toByteString())
  }

  @Test
  fun testSsbRotation() {
    val application = getApplicationContext<Application>()
    val se = SecureEnclaveImpl(application)
    val hkdf = HkdfImpl()
    val symmetricKeyEncryptor = SymmetricKeyEncryptorImpl()
    val ssb = SelfSovereignBackupImpl(
      se,
      symmetricKeyEncryptor,
      hkdf,
      skipAuthenticationForKeysInTestOnly = true
    )

    var keys = ssb.generateLocalWrappingKeys()

    val mockServer = MockServer(se, hkdf, symmetricKeyEncryptor)
    val bundle = mockServer.encrypt(keys)
    val ptServerKey = ssb.decryptServerKeyShare(bundle)

    assertEquals(serverPrivateKey, ptServerKey.toByteString())

    keys = ssb.rotateLocalWrappingKeyWithoutAuth()

    val bundle2 = mockServer.encrypt(keys)
    assertNotEquals(bundle, bundle2)

    val ptServerKey2 = ssb.decryptServerKeyShare(bundle2)
    assertEquals(serverPrivateKey, ptServerKey2.toByteString())
  }
}
