package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.HkdfImpl
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.Secp256k1SharedSecretImpl
import build.wallet.encrypt.SymmetricKeyGeneratorImpl
import build.wallet.encrypt.XChaCha20Poly1305Impl
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonceGeneratorImpl
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

class SocRecCryptoImplTests : FunSpec({
  val socRecCrypto =
    SocRecCryptoImpl(
      Secp256k1KeyGeneratorImpl(),
      SymmetricKeyGeneratorImpl(),
      XChaCha20Poly1305Impl(),
      Secp256k1SharedSecretImpl(),
      HkdfImpl(),
      XNonceGeneratorImpl()
    )

  test("encrypt and decrypt private key material") {
    // Enrollment
    val protectedCustomerIdentityKey =
      socRecCrypto.generateProtectedCustomerIdentityKey().getOrThrow()
    val trustedContactIdentityKey = socRecCrypto.generateTrustedContactIdentityKey().getOrThrow()

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
        trustedContactIdentityKey,
        protectedCustomerIdentityKey,
        privateKeyEncryptionKey
      ).getOrThrow()

    // Trusted Contact assists Protected Customer with recovery
    val protectedCustomerEphemeralKey =
      socRecCrypto.generateProtectedCustomerEphemeralKey().getOrThrow()
    val sealedSharedSecret =
      socRecCrypto.deriveAndEncryptSharedSecret(
        protectedCustomerIdentityKey,
        protectedCustomerEphemeralKey,
        trustedContactIdentityKey
      ).getOrThrow()

    // Decryption by Protected Customer
    socRecCrypto.decryptPrivateKeyMaterial(
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

  test("invalid keys") {
    // Valid keys
    val trustedContactIdentityKey = socRecCrypto.generateTrustedContactIdentityKey().getOrThrow()
    val protectedCustomerIdentityKey =
      socRecCrypto.generateProtectedCustomerIdentityKey().getOrThrow()
    val protectedCustomerEphemeralKey =
      socRecCrypto.generateProtectedCustomerEphemeralKey().getOrThrow()
    val privateKeyEncryptionKey = PrivateKeyEncryptionKey(SymmetricKeyGeneratorImpl().generate())

    // Invalid keys
    val (pubKey, _) = Secp256k1KeyGeneratorImpl().generateKeypair()
    val keypairWithMissingPrivateKey =
      AppKeyImpl(
        CurveType.SECP256K1,
        PublicKey(pubKey.value),
        null
      )
    val invalidPrivateKey =
      PrivateKey("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF".decodeHex())
    val keypairWithInvalidPrivateKey =
      AppKeyImpl(
        CurveType.SECP256K1,
        PublicKey(pubKey.value),
        invalidPrivateKey
      )

    // encryptPrivateKeyEncryptionKey
    socRecCrypto.encryptPrivateKeyEncryptionKey(
      trustedContactIdentityKey,
      ProtectedCustomerIdentityKey(ProtectedCustomerIdentityKey(keypairWithMissingPrivateKey)),
      privateKeyEncryptionKey
    ).shouldBeErr(SocRecCryptoError.EncryptionFailed(SocRecCryptoError.InvalidKeyType))
    socRecCrypto.encryptPrivateKeyEncryptionKey(
      trustedContactIdentityKey,
      ProtectedCustomerIdentityKey(keypairWithMissingPrivateKey),
      privateKeyEncryptionKey
    ).shouldBeErr(SocRecCryptoError.EncryptionFailed(SocRecCryptoError.PrivateKeyMissing))
    socRecCrypto.encryptPrivateKeyEncryptionKey(
      trustedContactIdentityKey,
      ProtectedCustomerIdentityKey(keypairWithInvalidPrivateKey),
      privateKeyEncryptionKey
    ).shouldBeErrOfType<SocRecCryptoError.EncryptionFailed>()

    // deriveAndEncryptSharedSecret
    socRecCrypto.deriveAndEncryptSharedSecret(
      protectedCustomerIdentityKey,
      protectedCustomerEphemeralKey,
      TrustedContactIdentityKey(TrustedContactIdentityKey(keypairWithMissingPrivateKey))
    ).shouldBeErr(SocRecCryptoError.SharedSecretEncryptionFailed(SocRecCryptoError.InvalidKeyType))
    socRecCrypto.deriveAndEncryptSharedSecret(
      protectedCustomerIdentityKey,
      protectedCustomerEphemeralKey,
      TrustedContactIdentityKey(keypairWithMissingPrivateKey)
    ).shouldBeErr(
      SocRecCryptoError.SharedSecretEncryptionFailed(SocRecCryptoError.PrivateKeyMissing)
    )
    socRecCrypto.deriveAndEncryptSharedSecret(
      protectedCustomerIdentityKey,
      protectedCustomerEphemeralKey,
      TrustedContactIdentityKey(keypairWithInvalidPrivateKey)
    ).shouldBeErrOfType<SocRecCryptoError.SharedSecretEncryptionFailed>()

    // decryptPrivateKeyMaterial
    val ciphertext = XCiphertext("")
    socRecCrypto.decryptPrivateKeyMaterial(
      protectedCustomerIdentityKey,
      trustedContactIdentityKey,
      ciphertext,
      DecryptPrivateKeyMaterialParams.V1(
        ciphertext,
        ProtectedCustomerEphemeralKey(ProtectedCustomerEphemeralKey(keypairWithMissingPrivateKey)),
        ciphertext
      )
    ).shouldBeErr(SocRecCryptoError.DecryptionFailed(SocRecCryptoError.InvalidKeyType))
    socRecCrypto.decryptPrivateKeyMaterial(
      protectedCustomerIdentityKey,
      trustedContactIdentityKey,
      ciphertext,
      DecryptPrivateKeyMaterialParams.V1(
        ciphertext,
        ProtectedCustomerEphemeralKey(keypairWithMissingPrivateKey),
        ciphertext
      )
    ).shouldBeErr(SocRecCryptoError.DecryptionFailed(SocRecCryptoError.PrivateKeyMissing))
    socRecCrypto.decryptPrivateKeyMaterial(
      protectedCustomerIdentityKey,
      trustedContactIdentityKey,
      ciphertext,
      DecryptPrivateKeyMaterialParams.V1(
        ciphertext,
        ProtectedCustomerEphemeralKey(keypairWithInvalidPrivateKey),
        ciphertext
      )
    ).shouldBeErrOfType<SocRecCryptoError.DecryptionFailed>()
  }
})
