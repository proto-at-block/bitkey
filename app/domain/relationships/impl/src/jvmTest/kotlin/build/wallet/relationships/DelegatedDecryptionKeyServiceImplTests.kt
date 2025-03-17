package build.wallet.relationships

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.coroutines.createBackgroundScope
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import okio.ByteString.Companion.encodeUtf8

class DelegatedDecryptionKeyServiceImplTests : FunSpec({

  val relationshipsKeysDao = RelationshipsKeysDaoFake()
  val accountConfigService = AccountConfigServiceFake()
  val cryptoBox = CryptoBoxImpl()
  val testKeypair = cryptoBox.generateKeyPair()
  lateinit var relationshipsF8eClient: RelationshipsF8eClientFake

  fun TestScope.delegatedDecryptionKeyService(): DelegatedDecryptionKeyService {
    relationshipsF8eClient = RelationshipsF8eClientFake(
      uuidGenerator = { "fake-uuid" },
      backgroundScope = createBackgroundScope(),
      clock = Clock.System
    )
    return DelegatedDecryptionKeyServiceImpl(
      cryptoBox = cryptoBox,
      relationshipsF8eClient = relationshipsF8eClient,
      relationshipsKeysDao = relationshipsKeysDao,
      accountConfigService = accountConfigService
    )
  }

  val fakeSealedData = "encoded-data".encodeUtf8()

  afterTest {
    relationshipsKeysDao.clear()
    relationshipsF8eClient.reset()
    accountConfigService.reset()
  }

  test("Uploading Sealed DDK Data") {
    val result = delegatedDecryptionKeyService().uploadSealedDelegatedDecryptionKeyData(
      fullAccountId = FullAccountMock.accountId,
      sealedData = fakeSealedData
    )

    result.isOk.shouldBeTrue()
  }

  test("Getting Sealed DDK Data") {
    val result = delegatedDecryptionKeyService().getSealedDelegatedDecryptionKeyData(
      FullAccountMock.accountId
    )

    result.isOk.shouldBeTrue()
    result.get().shouldBe(
      relationshipsF8eClient.ddkReturnData(
        accountId = FullAccountMock.accountId,
        f8eEnvironment = FullAccountMock.config.f8eEnvironment
      )
    )
  }

  test("Restoring Delegated Decryption Key") {
    val result = delegatedDecryptionKeyService().restoreDelegatedDecryptionKey(
      testKeypair.privateKey.bytes
    )
    result.isOk.shouldBeTrue()

    relationshipsKeysDao.getKeyWithPrivateMaterial(DelegatedDecryptionKey::class).shouldBe(
      Ok(
        AppKey(
          PublicKey(value = testKeypair.publicKey.bytes.hex()),
          PrivateKey(bytes = testKeypair.privateKey.bytes)
        )
      )
    )
  }
})
