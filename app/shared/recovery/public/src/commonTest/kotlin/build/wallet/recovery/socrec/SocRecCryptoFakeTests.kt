package build.wallet.recovery.socrec

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class SocRecCryptoFakeTests : FunSpec({
  val cryptoFake = SocRecCryptoFake()

  test("encrypt and decrypt private key material (version 1)") {
    // Enrollment
    val protectedCustomerIdentityKey =
      cryptoFake.generateProtectedCustomerIdentityKey().getOrThrow()
    val trustedContactIdentityKey = cryptoFake.generateTrustedContactIdentityKey().getOrThrow()

    // Protected Customer creates encrypted backup
    val privateKeyMaterial =
      "wsh(sortedmulti(2," +
        "[9d3902ae/84'/1'/0']" +
        "tpubDCU8xtEiG4DZ8J5qNsGrCNWzm4WzPBPM2nKTAiZqZfA6m2GMcva1n" +
        "GRLsiLKpLktmuJrdWg9XKxpcSd9uafyPSLfACwToyvk43XQVs8SH6P/*," +
        "[ff2d9449/84'/1'/0']" + "" +
        "tpubDGm8VUmd9iJKkfFhJCVTVfJx5ezF4iiwr5MrpjfaWNtot46fq2L5v" +
        "skeHLrccYKhBFfQ1BReoxwPRHaoUVAouFTTWyzqLVv3or8EBVHzFp5/*," +
        "[56a09b24/84'/1'/0']" +
        "tpubDDe5r54a9Ajy7dF8w16WCWegTJgGXceZNyBw2vkRczvwm1ZcRgiUE" +
        "8RUX7uHgExeNtbhrKVsQN4Eb24sWRrwoLDUmdxSeM4a3kgQrJr5m7P/*" +
        "))"
    val encryptedPrivateKeyMaterialOutput =
      cryptoFake.encryptPrivateKeyMaterial(privateKeyMaterial.toByteArray().toByteString())
        .getOrThrow()
    val privateKeyEncryptionKey = encryptedPrivateKeyMaterialOutput.privateKeyEncryptionKey
    val sealedPrivateKeyMaterial = encryptedPrivateKeyMaterialOutput.sealedPrivateKeyMaterial
    val sealedPrivateKeyEncryptionKey =
      cryptoFake.encryptPrivateKeyEncryptionKey(
        trustedContactIdentityKey,
        protectedCustomerIdentityKey,
        privateKeyEncryptionKey
      ).getOrThrow()

    // Trusted Contact assists Protected Customer with recovery
    val protectedCustomerEphemeralKey =
      cryptoFake.generateProtectedCustomerEphemeralKey().getOrThrow()
    val sealedSharedSecret =
      cryptoFake.deriveAndEncryptSharedSecret(
        protectedCustomerIdentityKey,
        protectedCustomerEphemeralKey,
        trustedContactIdentityKey
      ).getOrThrow()

    // Decryption by Protected Customer
    cryptoFake.decryptPrivateKeyMaterial(
      protectedCustomerIdentityKey,
      trustedContactIdentityKey,
      sealedPrivateKeyMaterial,
      DecryptPrivateKeyMaterialParams.V1(
        sealedSharedSecret,
        protectedCustomerEphemeralKey,
        sealedPrivateKeyEncryptionKey
      )
    ).getOrThrow().utf8().shouldBe(privateKeyMaterial)
  }

  test("sign and verify") {
    val (privKey, pubKey) = cryptoFake.generateKeyPair()
    val message = "Hello, World!".encodeUtf8()
    val signature = cryptoFake.sign(privKey, pubKey, message)
    cryptoFake.verifySig(signature, pubKey, message).shouldBe(true)

    val invalidMessage = "Hello, World?".encodeUtf8()
    cryptoFake.verifySig(signature, pubKey, invalidMessage).shouldBe(false)
  }

  test("encrypt and decrypt private key material (version 2)") {
    // Endorsement
    val hwEndorsementKeyPair =
      cryptoFake.generateKeyPair().let { (privKey, pubKey) ->
        AppGlobalAuthKeypair(
          AppGlobalAuthPublicKey(pubKey),
          AppGlobalAuthPrivateKey(privKey)
        )
      }
    val appEndorsementKeyPair =
      cryptoFake.generateKeyPair().let { (privKey, pubKey) ->
        AppGlobalAuthKeypair(
          AppGlobalAuthPublicKey(pubKey),
          AppGlobalAuthPrivateKey(privKey)
        )
      }
    val hwSignature =
      cryptoFake.sign(
        hwEndorsementKeyPair.privateKey.key,
        hwEndorsementKeyPair.publicKey.pubKey,
        appEndorsementKeyPair.publicKey.pubKey.value.decodeHex()
      )

    // Enrollment
    val protectedCustomerIdentityKey =
      cryptoFake.generateProtectedCustomerIdentityKey().getOrThrow()
    val trustedContactIdentityKey = cryptoFake.generateTrustedContactIdentityKey().getOrThrow()

    // Key authentication
    val enrollmentPassword = "enrollment password".encodeUtf8()
    val invalidPassword = "passworf".encodeUtf8()
    val protectedCustomerEnrollmentKey =
      cryptoFake.generateProtectedCustomerEnrollmentKey(enrollmentPassword).getOrThrow()
    val encryptTrustedContactIdentityKeyOutput =
      cryptoFake.encryptTrustedContactIdentityKey(
        enrollmentPassword,
        protectedCustomerEnrollmentKey,
        trustedContactIdentityKey
      ).getOrThrow()
    val decryptedTrustedContactIdentityKey =
      cryptoFake.decryptTrustedContactIdentityKey(
        enrollmentPassword,
        protectedCustomerEnrollmentKey,
        encryptTrustedContactIdentityKeyOutput
      ).getOrThrow()
    decryptedTrustedContactIdentityKey.publicKey.shouldBe(trustedContactIdentityKey.publicKey)
    shouldThrow<SocRecCryptoError.KeyConfirmationFailed> {
      cryptoFake.decryptTrustedContactIdentityKey(
        invalidPassword,
        protectedCustomerEnrollmentKey,
        encryptTrustedContactIdentityKeyOutput
      ).getOrThrow()
    }
    // Key certificate verification
    val keyCertificate =
      cryptoFake.generateKeyCertificate(
        decryptedTrustedContactIdentityKey,
        HwAuthPublicKey(hwEndorsementKeyPair.publicKey.pubKey),
        appEndorsementKeyPair,
        hwSignature
      ).getOrThrow()
    cryptoFake.verifyKeyCertificate(
      keyCertificate,
      HwAuthPublicKey(hwEndorsementKeyPair.publicKey.pubKey),
      null
    ).getOrThrow().publicKey.shouldBe(trustedContactIdentityKey.publicKey)
    cryptoFake.verifyKeyCertificate(
      keyCertificate,
      null,
      appEndorsementKeyPair.publicKey
    ).getOrThrow().publicKey.shouldBe(trustedContactIdentityKey.publicKey)
    // Invalid trusted keys
    shouldThrow<SocRecCryptoError.KeyCertificateVerificationFailed> {
      cryptoFake.verifyKeyCertificate(
        keyCertificate,
        null,
        null
      ).getOrThrow()
    }
    val invalidHwEndorsementKeyPair =
      cryptoFake.generateKeyPair().let { (privKey, pubKey) ->
        AppGlobalAuthKeypair(
          AppGlobalAuthPublicKey(pubKey),
          AppGlobalAuthPrivateKey(privKey)
        )
      }
    val invalidAppEndorsementKeyPair =
      cryptoFake.generateKeyPair().let { (privKey, pubKey) ->
        AppGlobalAuthKeypair(
          AppGlobalAuthPublicKey(pubKey),
          AppGlobalAuthPrivateKey(privKey)
        )
      }
    shouldThrow<SocRecCryptoError.KeyCertificateVerificationFailed> {
      cryptoFake.verifyKeyCertificate(
        keyCertificate,
        HwAuthPublicKey(invalidHwEndorsementKeyPair.publicKey.pubKey),
        null
      ).getOrThrow()
    }
    shouldThrow<SocRecCryptoError.KeyCertificateVerificationFailed> {
      cryptoFake.verifyKeyCertificate(
        keyCertificate,
        null,
        invalidAppEndorsementKeyPair.publicKey
      ).getOrThrow()
    }
    // Invalid key certificate
    val modifiedCertificate =
      keyCertificate.copy(
        appSignature = hwSignature
      )
    shouldThrow<SocRecCryptoError.KeyCertificateVerificationFailed> {
      cryptoFake.verifyKeyCertificate(
        modifiedCertificate,
        HwAuthPublicKey(hwEndorsementKeyPair.publicKey.pubKey),
        null
      ).getOrThrow()
    }

    // Protected Customer creates encrypted backup
    val privateKeyMaterial =
      "wsh(sortedmulti(2," +
        "[9d3902ae/84'/1'/0']" +
        "tpubDCU8xtEiG4DZ8J5qNsGrCNWzm4WzPBPM2nKTAiZqZfA6m2GMcva1n" +
        "GRLsiLKpLktmuJrdWg9XKxpcSd9uafyPSLfACwToyvk43XQVs8SH6P/*," +
        "[ff2d9449/84'/1'/0']" + "" +
        "tpubDGm8VUmd9iJKkfFhJCVTVfJx5ezF4iiwr5MrpjfaWNtot46fq2L5v" +
        "skeHLrccYKhBFfQ1BReoxwPRHaoUVAouFTTWyzqLVv3or8EBVHzFp5/*," +
        "[56a09b24/84'/1'/0']" +
        "tpubDDe5r54a9Ajy7dF8w16WCWegTJgGXceZNyBw2vkRczvwm1ZcRgiUE" +
        "8RUX7uHgExeNtbhrKVsQN4Eb24sWRrwoLDUmdxSeM4a3kgQrJr5m7P/*" +
        "))"
    val encryptedPrivateKeyMaterialOutput =
      cryptoFake.encryptPrivateKeyMaterial(privateKeyMaterial.toByteArray().toByteString())
        .getOrThrow()
    val privateKeyEncryptionKey = encryptedPrivateKeyMaterialOutput.privateKeyEncryptionKey
    val sealedPrivateKeyMaterial = encryptedPrivateKeyMaterialOutput.sealedPrivateKeyMaterial
    val sealedPrivateKeyEncryptionKey =
      cryptoFake.encryptPrivateKeyEncryptionKey(
        decryptedTrustedContactIdentityKey,
        protectedCustomerIdentityKey,
        privateKeyEncryptionKey
      ).getOrThrow()

    // Trusted Contact assists Protected Customer with recovery
    val recoveryPassword = "recovery password".encodeUtf8()
    val protectedCustomerRecoveryKey =
      cryptoFake.generateProtectedCustomerRecoveryKey(recoveryPassword).getOrThrow()
    val encryptedPrivateKeyEncryptionKeyOutput =
      cryptoFake.decryptPrivateKeyEncryptionKey(
        recoveryPassword,
        protectedCustomerRecoveryKey,
        protectedCustomerIdentityKey,
        trustedContactIdentityKey,
        sealedPrivateKeyEncryptionKey
      ).getOrThrow()

    // Decryption by Protected Customer
    shouldThrow<SocRecCryptoError.KeyConfirmationFailed> {
      cryptoFake.decryptPrivateKeyMaterial(
        protectedCustomerIdentityKey,
        decryptedTrustedContactIdentityKey,
        sealedPrivateKeyMaterial,
        DecryptPrivateKeyMaterialParams.V2(
          enrollmentPassword,
          protectedCustomerRecoveryKey,
          encryptedPrivateKeyEncryptionKeyOutput
        )
      ).getOrThrow()
    }
    cryptoFake.decryptPrivateKeyMaterial(
      protectedCustomerIdentityKey,
      decryptedTrustedContactIdentityKey,
      sealedPrivateKeyMaterial,
      DecryptPrivateKeyMaterialParams.V2(
        recoveryPassword,
        protectedCustomerRecoveryKey,
        encryptedPrivateKeyEncryptionKeyOutput
      )
    ).getOrThrow().utf8().shouldBe(privateKeyMaterial)
  }
})
