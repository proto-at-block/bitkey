package build.wallet.support

import bitkey.account.AccountConfigService
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.P256Box
import build.wallet.encrypt.P256BoxPublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonceGenerator
import build.wallet.f8e.support.EncryptedAttachmentF8eClient
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class EncryptedDescriptorAttachmentCryptoServiceImpl(
  private val p256Box: P256Box,
  private val xNonceGenerator: XNonceGenerator,
  private val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val encryptedAttachmentF8eClient: EncryptedAttachmentF8eClient,
  private val accountConfigService: AccountConfigService,
) : EncryptedDescriptorAttachmentCryptoService {
  override suspend fun encryptAndUploadDescriptor(
    accountId: AccountId,
    spendingKeysets: List<SpendingKeyset>,
  ): Result<String, Error> {
    return coroutineBinding {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      // Call f8e and get a public key and encrypted attachment ID back
      val attachment = encryptedAttachmentF8eClient.createEncryptedAttachment(
        accountId = accountId,
        f8eEnvironment = f8eEnvironment
      ).bind()
      // Encrypt the descriptor
      val sealedAttachment = p256BoxEncrypt(attachment.publicKey, spendingKeysets)
      // Upload the encrypted descriptor
      encryptedAttachmentF8eClient.uploadSealedAttachment(
        accountId = accountId,
        f8eEnvironment = f8eEnvironment,
        encryptedAttachmentId = attachment.encryptedAttachmentId,
        sealedAttachment = sealedAttachment.value
      ).bind()

      // Return the encrypted attachment id
      return@coroutineBinding attachment.encryptedAttachmentId
    }
  }

  internal fun p256BoxEncrypt(
    publicKey: ByteString,
    spendingKeysets: List<SpendingKeyset>,
  ): XCiphertext {
    val descriptorKeysetHashMap = spendingKeysets.associate { spendingKeyset ->
      val descriptor = bitcoinMultiSigDescriptorBuilder
        .watchingDescriptor(
          appPublicKey = spendingKeyset.appKey.key,
          hardwareKey = spendingKeyset.hardwareKey.key,
          serverKey = spendingKeyset.f8eSpendingKeyset.spendingPublicKey.key
        ).raw
      spendingKeyset.f8eSpendingKeyset.keysetId to descriptor
    }
    // Generate a new key pair for each encryption operation
    val keys = p256Box.generateKeyPair()
    // Encrypt the raw wallet descriptor using the provided public key and the generated private key
    return p256Box.encrypt(
      theirPublicKey = P256BoxPublicKey(publicKey),
      myKeyPair = keys,
      nonce = xNonceGenerator.generateXNonce(),
      plaintext = Json.encodeToString(descriptorKeysetHashMap).encodeUtf8()
    )
  }
}
