package build.wallet.relationships

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.SignatureVerifierImpl
import build.wallet.encrypt.toSecp256k1PrivateKey
import build.wallet.encrypt.toSecp256k1PublicKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class RelationshipsCryptoFakeTests : FunSpec({
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val cryptoFake = RelationshipsCryptoFake(
    messageSigner = MessageSignerImpl(),
    signatureVerifier = SignatureVerifierImpl(),
    appPrivateKeyDao = appPrivateKeyDao
  )

  beforeTest {
    appPrivateKeyDao.reset()
  }

  test("encrypt and decrypt private key material") {
    // Endorsement
    val hwEndorsementKeyPair = cryptoFake.generateAppAuthKeypair()
    val appEndorsementKeyPair = cryptoFake.generateAppAuthKeypair()
    val hwSignature =
      cryptoFake
        .sign(
          privateKey = hwEndorsementKeyPair.privateKey.shouldNotBeNull().toSecp256k1PrivateKey(),
          message = appEndorsementKeyPair.publicKey.value.encodeUtf8()
        )
        .hex()
        .let(::AppGlobalAuthKeyHwSignature)

    val invalidHwEndorsementKeyPair = cryptoFake.generateAppAuthKeypair()
    val invalidAppEndorsementKeyPair = cryptoFake.generateAppAuthKeypair()

    // Enrollment
    val trustedContactIdentityKey = cryptoFake.generateDelegatedDecryptionKey().getOrThrow()

    // Key authentication
    val enrollmentCode = PakeCode("F00DBAR".toByteArray().toByteString())
    val invalidEnrollmentCode = PakeCode("F00DBAN".toByteArray().toByteString())
    val protectedCustomerEnrollmentPakeKey =
      cryptoFake.generateProtectedCustomerEnrollmentPakeKey(enrollmentCode).getOrThrow()
    val encryptTrustedContactIdentityKeyOutput =
      cryptoFake.encryptDelegatedDecryptionKey(
        enrollmentCode,
        protectedCustomerEnrollmentPakeKey.publicKey,
        trustedContactIdentityKey.publicKey
      ).getOrThrow()
    val decryptedTrustedContactIdentityKey =
      cryptoFake.decryptDelegatedDecryptionKey(
        enrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        encryptTrustedContactIdentityKeyOutput
      ).getOrThrow()
    decryptedTrustedContactIdentityKey.shouldBe(trustedContactIdentityKey.publicKey)
    // Invalid password
    shouldThrow<RelationshipsCryptoError.KeyConfirmationFailed> {
      cryptoFake.decryptDelegatedDecryptionKey(
        invalidEnrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        encryptTrustedContactIdentityKeyOutput
      ).getOrThrow()
    }
    val invalidEncryptTrustedContactIdentityKeyOutput =
      cryptoFake.encryptDelegatedDecryptionKey(
        invalidEnrollmentCode,
        protectedCustomerEnrollmentPakeKey.publicKey,
        trustedContactIdentityKey.publicKey
      ).getOrThrow()
    shouldThrow<RelationshipsCryptoError.KeyConfirmationFailed> {
      cryptoFake.decryptDelegatedDecryptionKey(
        invalidEnrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        invalidEncryptTrustedContactIdentityKeyOutput
      ).getOrThrow()
    }
    shouldThrow<RelationshipsCryptoError.KeyConfirmationFailed> {
      cryptoFake.decryptDelegatedDecryptionKey(
        enrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        invalidEncryptTrustedContactIdentityKeyOutput
      ).getOrThrow()
    }
    // Key certificate verification
    // Can verify when both keys are valid
    val keyCertificate =
      cryptoFake.generateKeyCertificate(
        delegatedDecryptionKey = decryptedTrustedContactIdentityKey,
        hwAuthKey = HwAuthPublicKey(hwEndorsementKeyPair.publicKey.toSecp256k1PublicKey()),
        appGlobalAuthKey = appEndorsementKeyPair.publicKey,
        appGlobalAuthKeyHwSignature = hwSignature
      ).getOrThrow()
    // Can verify with app auth key only
    cryptoFake.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = null,
      appGlobalAuthKey = appEndorsementKeyPair.publicKey
    ).getOrThrow().shouldBe(trustedContactIdentityKey.publicKey)
    // Can verify with hw auth key only
    cryptoFake.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = HwAuthPublicKey(hwEndorsementKeyPair.publicKey.toSecp256k1PublicKey()),
      appGlobalAuthKey = null
    ).getOrThrow().shouldBe(trustedContactIdentityKey.publicKey)
    // Can verify if at least app auth key is valid
    cryptoFake.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = HwAuthPublicKey(invalidHwEndorsementKeyPair.publicKey.toSecp256k1PublicKey()),
      appGlobalAuthKey = appEndorsementKeyPair.publicKey
    ).getOrThrow().shouldBe(trustedContactIdentityKey.publicKey)
    // Can verify if at least hw auth key is valid
    cryptoFake.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = HwAuthPublicKey(hwEndorsementKeyPair.publicKey.toSecp256k1PublicKey()),
      appGlobalAuthKey = invalidAppEndorsementKeyPair.publicKey
    ).getOrThrow().shouldBe(trustedContactIdentityKey.publicKey)
    // Both auth keys are not provided
    cryptoFake.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = null,
      appGlobalAuthKey = null
    ).shouldBe(Err(RelationshipsCryptoError.AuthKeysNotPresent))
    // Invalid trusted keys
    shouldThrow<RelationshipsCryptoError.KeyCertificateVerificationFailed> {
      cryptoFake.verifyKeyCertificate(
        keyCertificate,
        HwAuthPublicKey(invalidHwEndorsementKeyPair.publicKey.toSecp256k1PublicKey()),
        invalidAppEndorsementKeyPair.publicKey
      ).getOrThrow()
    }
    // Invalid key certificate
    val modifiedCertificate =
      keyCertificate.copy(
        appGlobalAuthPublicKey = invalidAppEndorsementKeyPair.publicKey
      )
    shouldThrow<RelationshipsCryptoError.KeyCertificateVerificationFailed> {
      cryptoFake.verifyKeyCertificate(
        modifiedCertificate,
        HwAuthPublicKey(hwEndorsementKeyPair.publicKey.toSecp256k1PublicKey()),
        invalidAppEndorsementKeyPair.publicKey
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
        privateKeyEncryptionKey
      ).getOrThrow()

    // Trusted Contact assists Protected Customer with recovery
    val recoveryCode = PakeCode("12345678901".toByteArray().toByteString())
    val invalidRecoveryCode = PakeCode("12345678991".toByteArray().toByteString())
    val protectedCustomerRecoveryPakeKey =
      cryptoFake.generateProtectedCustomerRecoveryPakeKey(recoveryCode).getOrThrow()
    val encryptedPrivateKeyEncryptionKeyOutput =
      cryptoFake.transferPrivateKeyEncryptionKeyEncryption(
        recoveryCode,
        protectedCustomerRecoveryPakeKey.publicKey,
        trustedContactIdentityKey,
        sealedPrivateKeyEncryptionKey
      ).getOrThrow()

    // Decryption by Protected Customer
    // TODO: add more invalid code tests, ala enrollment code
    shouldThrow<RelationshipsCryptoError.KeyConfirmationFailed> {
      cryptoFake.decryptPrivateKeyMaterial(
        invalidRecoveryCode,
        protectedCustomerRecoveryPakeKey,
        encryptedPrivateKeyEncryptionKeyOutput,
        sealedPrivateKeyMaterial
      ).getOrThrow()
    }
    cryptoFake.decryptPrivateKeyMaterial(
      recoveryCode,
      protectedCustomerRecoveryPakeKey,
      encryptedPrivateKeyEncryptionKeyOutput,
      sealedPrivateKeyMaterial
    ).getOrThrow().utf8().shouldBe(privateKeyMaterial)
  }
})
