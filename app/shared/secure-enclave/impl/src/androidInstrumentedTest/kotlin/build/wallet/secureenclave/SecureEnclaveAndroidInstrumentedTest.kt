package build.wallet.secureenclave

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import build.wallet.logging.logTesting
import build.wallet.secureenclave.SeKeyPurpose.AGREEMENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.ProviderException

@RunWith(AndroidJUnit4::class)
class SecureEnclaveAndroidInstrumentedTest {
  @Test
  fun testGenerateP256KeyPair() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val se = SecureEnclaveImpl(context)
    val keySpec = SeKeySpec(
      name = "key-testGenerateP256KeyPair",
      purposes = SeKeyPurposes.of(AGREEMENT),
      usageConstraints = SeKeyUsageConstraints.NONE,
      validity = null
    )
    val keyPair = se.generateP256KeyPair(keySpec)
    logTesting { "Public key: ${keyPair.publicKey.bytes.toHexString()}" }
  }

  @Test
  fun testDiffieHellman() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val se = SecureEnclaveImpl(context)

    val clientKeyPair = se.generateP256KeyPair(
      SeKeySpec(
        name = "key-testDiffieHellman-client",
        purposes = SeKeyPurposes.of(AGREEMENT),
        usageConstraints = SeKeyUsageConstraints.NONE,
        validity = null
      )
    )
    val clientPublicKey = se.publicKeyForPrivateKey(clientKeyPair.privateKey)

    val serverKeyPair = se.generateP256KeyPair(
      SeKeySpec(
        name = "key-testDiffieHellman-server",
        purposes = SeKeyPurposes.of(AGREEMENT),
        usageConstraints = SeKeyUsageConstraints.NONE,
        validity = null
      )
    )
    val serverPublicKey = se.publicKeyForPrivateKey(serverKeyPair.privateKey)

    val sharedSecret1 = se.diffieHellman(clientKeyPair.privateKey, serverPublicKey)
    val sharedSecret2 = se.diffieHellman(serverKeyPair.privateKey, clientPublicKey)
    logTesting { "Shared secret 1: ${sharedSecret1.toHexString()}" }
    logTesting { "Shared secret 2: ${sharedSecret2.toHexString()}" }
    assert(sharedSecret1.contentEquals(sharedSecret2))
  }

  @Test
  fun testKeyUsageConstraints() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val se = SecureEnclaveImpl(context)

    val keySpec = SeKeySpec(
      name = "key-testKeyUsageConstraints",
      purposes = SeKeyPurposes.of(AGREEMENT),
      usageConstraints = SeKeyUsageConstraints.BIOMETRICS_OR_PIN_REQUIRED,
      validity = SeKeyValidity.RequiredForEveryUse
    )

    val keyPair = se.generateP256KeyPair(keySpec)

    // Try to use the key, expecting a ProviderException due to missing authentication
    val exception = assertThrows(ProviderException::class.java) {
      se.diffieHellman(keyPair.privateKey, keyPair.publicKey)
    }

    val expectedMessage = "Keystore operation failed"
    assertEquals(expectedMessage, exception.message)
  }

  @Test
  fun loadPublicKey() {
    val sec1Pubkey = "04e952c94ecd6d4438edaf8939f9164533dedb1c6e822534f800f60f3a116054f47c783dca8bc5193e4cb71c870c0696f7d3d9ed9716413cfeb293f879ee8a9e73"

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val se = SecureEnclaveImpl(context)

    se.loadSePublicKey(SePublicKey(sec1Pubkey.hexToByteArray()))
  }
}
