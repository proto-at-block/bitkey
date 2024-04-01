package build.wallet.recovery.socrec

import build.wallet.auth.AppAuthKeyMessageSignerImpl
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.TcIdentityKeyAppSignature
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.crypto.Spake2Impl
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.SignatureVerifierImpl
import build.wallet.encrypt.SymmetricKeyGeneratorImpl
import build.wallet.encrypt.XChaCha20Poly1305Impl
import build.wallet.encrypt.XNonceGeneratorImpl
import build.wallet.encrypt.signResult
import build.wallet.encrypt.toPrivateKey
import build.wallet.encrypt.toPublicKey
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

@Suppress("UNCHECKED_CAST")
class SocRecCryptoImplTests : FunSpec({
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val messageSigner = MessageSignerImpl()
  val appAuthKeyMessageSigner = AppAuthKeyMessageSignerImpl(appPrivateKeyDao, messageSigner)
  val secp256k1KeyGenerator = Secp256k1KeyGeneratorImpl()
  val socRecCrypto =
    SocRecCryptoImpl(
      SymmetricKeyGeneratorImpl(),
      XChaCha20Poly1305Impl(),
      CryptoBoxImpl(),
      XNonceGeneratorImpl(),
      Spake2Impl(),
      appAuthKeyMessageSigner,
      SignatureVerifierImpl()
    )

  test("encrypt and decrypt private key material") {
    // Endorsement
    val (hwPubKey, hwPrivKey) = secp256k1KeyGenerator.generateKeypair()
    val (appPubKey, appPrivKey) = secp256k1KeyGenerator.generateKeypair()
    appPrivateKeyDao.asymmetricKeys[appPubKey.toPublicKey<AppGlobalAuthKey>()] =
      appPrivKey.toPrivateKey<AppGlobalAuthKey>()
    val hwSignature = messageSigner.signResult(appPubKey.value.encodeUtf8(), hwPrivKey).getOrThrow()

    val invalidHwEndorsementPublicKey =
      HwAuthPublicKey(secp256k1KeyGenerator.generateKeypair().publicKey)
    val invalidAppEndorsementPublicKey =
      secp256k1KeyGenerator.generateKeypair().publicKey.toPublicKey<AppGlobalAuthKey>()

    // Enrollment
    val delegatedDecryptionKey = socRecCrypto.generateDelegatedDecryptionKey().getOrThrow()

    // Key authentication
    val enrollmentCode = PakeCode("F00DBAR".toByteArray().toByteString())
    val invalidEnrollmentCode = PakeCode("F00DBAN".toByteArray().toByteString())
    val protectedCustomerEnrollmentPakeKey =
      socRecCrypto.generateProtectedCustomerEnrollmentPakeKey(enrollmentCode).getOrThrow()
    val encryptDelegatedDecryptionKeyOutput =
      socRecCrypto.encryptDelegatedDecryptionKey(
        enrollmentCode,
        protectedCustomerEnrollmentPakeKey.publicKey,
        delegatedDecryptionKey.publicKey
      ).getOrThrow()
    val decryptedDelegatedDecryptionKey =
      socRecCrypto.decryptDelegatedDecryptionKey(
        enrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        encryptDelegatedDecryptionKeyOutput
      ).getOrThrow()
    decryptedDelegatedDecryptionKey.shouldBe(delegatedDecryptionKey.publicKey)
    // Invalid password
    shouldThrow<SocRecCryptoError.DecryptionFailed> {
      socRecCrypto.decryptDelegatedDecryptionKey(
        invalidEnrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        encryptDelegatedDecryptionKeyOutput
      ).getOrThrow()
    }
    val invalidEncryptDelegatedDecryptionKeyOutput =
      socRecCrypto.encryptDelegatedDecryptionKey(
        invalidEnrollmentCode,
        protectedCustomerEnrollmentPakeKey.publicKey,
        delegatedDecryptionKey.publicKey
      ).getOrThrow()
    shouldThrow<SocRecCryptoError.DecryptionFailed> {
      socRecCrypto.decryptDelegatedDecryptionKey(
        invalidEnrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        invalidEncryptDelegatedDecryptionKeyOutput
      ).getOrThrow()
    }
    shouldThrow<SocRecCryptoError.DecryptionFailed> {
      socRecCrypto.decryptDelegatedDecryptionKey(
        enrollmentCode,
        protectedCustomerEnrollmentPakeKey,
        invalidEncryptDelegatedDecryptionKeyOutput
      ).getOrThrow()
    }
    // Key certificate verification
    // Can verify when both keys are valid
    val keyCertificate =
      socRecCrypto.generateKeyCertificate(
        decryptedDelegatedDecryptionKey,
        HwAuthPublicKey(hwPubKey),
        appPubKey.toPublicKey(),
        AppGlobalAuthKeyHwSignature(hwSignature)
      ).getOrThrow()
    // Can verify with app auth key only
    socRecCrypto.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = null,
      appGlobalAuthKey = appPubKey.toPublicKey()
    ).getOrThrow().shouldBe(delegatedDecryptionKey.publicKey)
    // Can verify with hw auth key only
    socRecCrypto.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = HwAuthPublicKey(hwPubKey),
      appGlobalAuthKey = null
    ).getOrThrow().shouldBe(delegatedDecryptionKey.publicKey)
    // Can verify if at least hw auth key is valid
    socRecCrypto.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = HwAuthPublicKey(hwPubKey),
      appGlobalAuthKey = invalidAppEndorsementPublicKey
    ).getOrThrow().shouldBe(delegatedDecryptionKey.publicKey)
    // Can verify if at least app auth key is valid
    socRecCrypto.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = invalidHwEndorsementPublicKey,
      appGlobalAuthKey = appPubKey.toPublicKey()
    ).getOrThrow().shouldBe(delegatedDecryptionKey.publicKey)
    // Both auth keys are not provided
    socRecCrypto.verifyKeyCertificate(
      keyCertificate = keyCertificate,
      hwAuthKey = null,
      appGlobalAuthKey = null
    ).shouldBeErr(SocRecCryptoError.AuthKeysNotPresent)
    // Invalid trusted keys
    shouldThrow<SocRecCryptoError.KeyCertificateVerificationFailed> {
      socRecCrypto.verifyKeyCertificate(
        keyCertificate,
        invalidHwEndorsementPublicKey,
        invalidAppEndorsementPublicKey
      ).getOrThrow()
    }
    // Invalid key certificate
    val modifiedCertificate =
      keyCertificate.copy(
        trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignature(hwSignature)
      )
    shouldThrow<SocRecCryptoError.KeyCertificateVerificationFailed> {
      socRecCrypto.verifyKeyCertificate(
        modifiedCertificate,
        HwAuthPublicKey(hwPubKey),
        invalidAppEndorsementPublicKey
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
      socRecCrypto.encryptPrivateKeyMaterial(privateKeyMaterial.toByteArray().toByteString())
        .getOrThrow()
    val privateKeyEncryptionKey = encryptedPrivateKeyMaterialOutput.privateKeyEncryptionKey
    val sealedPrivateKeyMaterial = encryptedPrivateKeyMaterialOutput.sealedPrivateKeyMaterial
    val sealedPrivateKeyEncryptionKey =
      socRecCrypto.encryptPrivateKeyEncryptionKey(
        decryptedDelegatedDecryptionKey,
        privateKeyEncryptionKey
      ).getOrThrow()

    // Trusted Contact assists Protected Customer with recovery
    val recoveryCode = PakeCode("12345678901".toByteArray().toByteString())
    val invalidRecoveryCode = PakeCode("12345678991".toByteArray().toByteString())
    val protectedCustomerRecoveryPakeKey =
      socRecCrypto.generateProtectedCustomerRecoveryPakeKey(recoveryCode).getOrThrow()
    val decryptPrivateKeyEncryptionKeyOutput =
      socRecCrypto.decryptPrivateKeyEncryptionKey(
        recoveryCode,
        protectedCustomerRecoveryPakeKey.publicKey,
        delegatedDecryptionKey,
        sealedPrivateKeyEncryptionKey
      ).getOrThrow()

    // Decryption by Protected Customer
    shouldThrow<SocRecCryptoError.DecryptionFailed> {
      socRecCrypto.decryptPrivateKeyMaterial(
        invalidRecoveryCode,
        protectedCustomerRecoveryPakeKey,
        decryptPrivateKeyEncryptionKeyOutput,
        sealedPrivateKeyMaterial
      ).getOrThrow()
    }
    val invalidEncryptedPrivateKeyEncryptionKeyOutput =
      socRecCrypto.decryptPrivateKeyEncryptionKey(
        recoveryCode,
        protectedCustomerRecoveryPakeKey.publicKey,
        delegatedDecryptionKey,
        sealedPrivateKeyEncryptionKey
      ).getOrThrow()
    shouldThrow<SocRecCryptoError.DecryptionFailed> {
      socRecCrypto.decryptPrivateKeyMaterial(
        invalidRecoveryCode,
        protectedCustomerRecoveryPakeKey,
        invalidEncryptedPrivateKeyEncryptionKeyOutput,
        sealedPrivateKeyMaterial
      ).getOrThrow()
    }
    shouldThrow<SocRecCryptoError.DecryptionFailed> {
      socRecCrypto.decryptPrivateKeyMaterial(
        invalidRecoveryCode,
        protectedCustomerRecoveryPakeKey,
        invalidEncryptedPrivateKeyEncryptionKeyOutput,
        sealedPrivateKeyMaterial
      ).getOrThrow()
    }
    socRecCrypto.decryptPrivateKeyMaterial(
      recoveryCode,
      protectedCustomerRecoveryPakeKey,
      decryptPrivateKeyEncryptionKeyOutput,
      sealedPrivateKeyMaterial
    ).getOrThrow().utf8().shouldBe(privateKeyMaterial)
  }

  test("invalid keys") {
    // Valid keys
    val delegatedDecryptionKey = socRecCrypto.generateDelegatedDecryptionKey().getOrThrow()
    val privateKeyEncryptionKey = PrivateKeyEncryptionKey(SymmetricKeyGeneratorImpl().generate())
    val sealedPrivateKeyEncryptionKey =
      socRecCrypto.encryptPrivateKeyEncryptionKey(
        delegatedDecryptionKey.publicKey,
        privateKeyEncryptionKey
      ).getOrThrow()
    val recoveryCode = PakeCode("12345678901".toByteArray().toByteString())
    val protectedCustomerRecoveryPakeKey =
      socRecCrypto.generateProtectedCustomerRecoveryPakeKey(recoveryCode).getOrThrow()
    val encryptedPrivateKeyEncryptionKeyOutput =
      socRecCrypto.decryptPrivateKeyEncryptionKey(
        recoveryCode,
        protectedCustomerRecoveryPakeKey.publicKey,
        delegatedDecryptionKey,
        sealedPrivateKeyEncryptionKey
      ).getOrThrow()

    // Invalid keys
    val (pubKey, _) = Secp256k1KeyGeneratorImpl().generateKeypair()
    val invalidPrivateKey =
      PrivateKey<Nothing>("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF".decodeHex())
    val keypairWithInvalidPrivateKey =
      AppKey<Nothing>(
        PublicKey(pubKey.value),
        invalidPrivateKey
      )

    // decryptPrivateKeyEncryptionKey
    socRecCrypto.decryptPrivateKeyEncryptionKey(
      recoveryCode,
      protectedCustomerRecoveryPakeKey.publicKey,
      keypairWithInvalidPrivateKey as AppKey<DelegatedDecryptionKey>,
      sealedPrivateKeyEncryptionKey
    ).shouldBeErrOfType<SocRecCryptoError.DecryptionFailed>()

    // decryptPrivateKeyMaterial
    socRecCrypto.decryptPrivateKeyMaterial(
      recoveryCode,
      keypairWithInvalidPrivateKey as AppKey<ProtectedCustomerRecoveryPakeKey>,
      encryptedPrivateKeyEncryptionKeyOutput,
      sealedPrivateKeyEncryptionKey
    ).shouldBeErrOfType<SocRecCryptoError.DecryptionFailed>()
  }
})
