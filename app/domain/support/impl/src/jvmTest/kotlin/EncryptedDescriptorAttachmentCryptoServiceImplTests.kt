import bitkey.account.AccountConfigServiceFake
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.encrypt.P256BoxImpl
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonce
import build.wallet.encrypt.XNonceGeneratorMock
import build.wallet.f8e.support.EncryptedAttachmentF8eClientMock
import build.wallet.support.EncryptedAttachment
import build.wallet.support.EncryptedDescriptorAttachmentCryptoServiceImpl
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex

class EncryptedDescriptorAttachmentCryptoServiceImplTests : FunSpec({

  val p256Box = P256BoxImpl()
  val xNonceGenerator = XNonceGeneratorMock()
  val bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderMock()
  val encryptedAttachmentF8eClient = EncryptedAttachmentF8eClientMock()
  val accountConfigService = AccountConfigServiceFake()

  lateinit var service: EncryptedDescriptorAttachmentCryptoServiceImpl

  beforeTest {
    bitcoinMultiSigDescriptorBuilder.reset()
    encryptedAttachmentF8eClient.reset()
    accountConfigService.reset()
    xNonceGenerator.generateXNonceResult = XNonce("d6c7f8b911a8e0d16f3cc52c92ab0f719ef9e8cb54f02b63".decodeHex())
    service = EncryptedDescriptorAttachmentCryptoServiceImpl(
      p256Box = p256Box,
      xNonceGenerator = xNonceGenerator,
      bitcoinMultiSigDescriptorBuilder = bitcoinMultiSigDescriptorBuilder,
      encryptedAttachmentF8eClient = encryptedAttachmentF8eClient,
      accountConfigService = accountConfigService
    )
  }

  test("encryptAndUploadDescriptor - happy path") {
    val spendingKeyset1 = SpendingKeysetMock
    val spendingKeyset2 = PrivateSpendingKeysetMock
    val spendingKeysets = listOf(spendingKeyset1, spendingKeyset2)

    val keyset = p256Box.generateKeyPair()
    val attachmentId = "urn:wallet-keyset:01JWF84NT25TR4GYQQB564NSZK"

    val encryptedAttachment = EncryptedAttachment(
      encryptedAttachmentId = attachmentId,
      publicKey = keyset.publicKey.bytes
    )

    encryptedAttachmentF8eClient.createEncryptedAttachmentResult = Ok(encryptedAttachment)
    encryptedAttachmentF8eClient.uploadSealedAttachmentResult = Ok(Unit)

    val accountId = FullAccountId("server-id")
    val result = service.encryptAndUploadDescriptor(
      accountId = accountId,
      spendingKeysets = spendingKeysets
    )

    result.shouldBe(Ok(attachmentId))
  }

  test("p256BoxEncrypt descriptor map consistency") {
    val spendingKeyset1 = SpendingKeysetMock
    val spendingKeyset2 = PrivateSpendingKeysetMock
    val spendingKeysets = listOf(spendingKeyset1, spendingKeyset2)

    val keyset = p256Box.generateKeyPair()
    // The server-returned public key
    val publicKey = keyset.publicKey.bytes
    val attachmentId = "urn:wallet-keyset:01JWF84NT25TR4GYQQB564NSZK"
    val encryptedAttachment = EncryptedAttachment(
      encryptedAttachmentId = attachmentId,
      publicKey = publicKey
    )
    encryptedAttachmentF8eClient.createEncryptedAttachmentResult = Ok(encryptedAttachment)
    bitcoinMultiSigDescriptorBuilder.watchingDescriptorResult = "wsh(sortedmulti(2,[abc123/84h/0h/0h/0h]xpub1/0;1/*,[def456/84h/0h/0h]xpub2/0;1/*,[ghi789/84h/0h/0h/0]xpub3/0;1/*))"

    val result = service.p256BoxEncrypt(
      publicKey = publicKey,
      spendingKeysets = spendingKeysets
    )
    // to verify we're encrypting the material consistently
    val decryptedXCiphertext = p256Box.decrypt(keyset.privateKey, XCiphertext(result.value)).hex()
    val expected = "7b226638652d7370656e64696e672d6b65797365742d6964223a2277736828736f727465646d756c746928322c5b6162633132332f3834682f30682f30682f30685d78707562312f303b312f2a2c5b6465663435362f3834682f30682f30685d78707562322f303b312f2a2c5b6768693738392f3834682f30682f30682f305d78707562332f303b312f2a2929222c227370656e64696e672d7075626c69632d6b65797365742d66616b652d7365727665722d69642d32223a2277736828736f727465646d756c746928322c5b6162633132332f3834682f30682f30682f30685d78707562312f303b312f2a2c5b6465663435362f3834682f30682f30685d78707562322f303b312f2a2c5b6768693738392f3834682f30682f30682f305d78707562332f303b312f2a2929227d"
    decryptedXCiphertext.shouldBe(expected)
  }
})
