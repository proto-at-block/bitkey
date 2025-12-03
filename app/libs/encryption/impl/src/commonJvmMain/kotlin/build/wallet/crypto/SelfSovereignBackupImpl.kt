package build.wallet.crypto

import build.wallet.encrypt.Hkdf
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.secureenclave.*
import okio.ByteString.Companion.toByteString

private data class SSBLocalWrappingKeys(
  // Local wrapping key with authentication requirements.
  val lka: SeKeyPair,
  // Local wrapping key WITHOUT authentication requirements.
  val lkn: SeKeyPair,
)

/**
 *  Section 4.1 of the whitepaper.
 */
class SelfSovereignBackupImpl(
  private val secureEnclave: SecureEnclave,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val hkdf: Hkdf,
  private val skipAuthenticationForKeysInTestOnly: Boolean = false,
) : SelfSovereignBackup {
  private val lkaName = "SSBLocalWrappingKeyWithAuth"
  private val lknName = "SSBLocalWrappingKeyNoAuth"

  private fun getLocalWrappingKeys(): SSBLocalWrappingKeys {
    // Get the local wrapping keys from the secure enclave.
    val lka = secureEnclave.loadKeyPair(lkaName)
    val lkn = secureEnclave.loadKeyPair(lknName)
    return SSBLocalWrappingKeys(lka, lkn)
  }

  private fun generateLka() {
    val lkaUsageConstraints = if (skipAuthenticationForKeysInTestOnly) {
      SeKeyUsageConstraints.NONE
    } else {
      // PIN is required for LKA: no biometrics. Why? Because on iOS, it's too easy for someone to
      // accidentally authenticate with FaceID.
      SeKeyUsageConstraints.PIN_REQUIRED
    }

    val lkaValidity = if (skipAuthenticationForKeysInTestOnly) {
      null
    } else {
      SeKeyValidity.RequiredForEveryUse
    }

    secureEnclave
      .generateP256KeyPair(
        SeKeySpec(
          name = lkaName,
          purposes = SeKeyPurposes.of(SeKeyPurpose.AGREEMENT),
          usageConstraints = lkaUsageConstraints,
          validity = lkaValidity
        )
      ).privateKey
  }

  private fun generateLkn() {
    secureEnclave
      .generateP256KeyPair(
        SeKeySpec(
          name = lknName,
          purposes = SeKeyPurposes.of(SeKeyPurpose.AGREEMENT),
          usageConstraints = SeKeyUsageConstraints.NONE,
          validity = null
        )
      ).privateKey
  }

  override fun generateLocalWrappingKeys(): SSBLocalWrappingPublicKeys {
    generateLka()
    generateLkn()
    return exportLocalWrappingPublicKeys()
  }

  override fun rotateLocalWrappingKeyWithoutAuth(): SSBLocalWrappingPublicKeys {
    // Rotate only the LKN. This (plus the double ECDH scheme) allows us to rotate the wrapping
    // keys without requiring user interaction.
    generateLkn()
    return exportLocalWrappingPublicKeys()
  }

  override fun exportLocalWrappingPublicKeys(): SSBLocalWrappingPublicKeys {
    val (lka, lkn) = getLocalWrappingKeys()
    return SSBLocalWrappingPublicKeys(
      lka.publicKey,
      lkn.publicKey
    )
  }

  override fun decryptServerKeyShare(bundle: SSBServerBundle): SSBServerPlaintextKeyShare {
    val (lka, lkn) = getLocalWrappingKeys()

    val s1 =
      secureEnclave
        .diffieHellman(
          lka.privateKey,
          SePublicKey(
            bytes = bundle.ephemeralPublicKey
              .toByteArray()
          )
        )
    val s2 =
      secureEnclave
        .diffieHellman(
          lkn.privateKey,
          SePublicKey(
            bytes = bundle.ephemeralPublicKey
              .toByteArray()
          )
        )

    val okm = hkdf
      .deriveKey(
        ikm = (s1 + s2).toByteString(),
        salt = null,
        info = null,
        outputLength = 32
      )

    return symmetricKeyEncryptor
      .unsealNoMetadata(
        bundle.sealedServerKeyShare,
        okm
      ).toByteArray()
  }
}
