package build.wallet.relationships

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.coroutines.createBackgroundScope
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import okio.ByteString.Companion.encodeUtf8

class DelegatedDecryptionKeyServiceImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val relationshipsKeysDao = RelationshipsKeysDaoFake()
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
      accountService = accountService,
      cryptoBox = cryptoBox,
      relationshipsF8eClient = relationshipsF8eClient,
      relationshipsKeysDao = relationshipsKeysDao
    )
  }

  val fakeSealedData = "encoded-data".encodeUtf8()

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
  }

  afterTest {
    relationshipsKeysDao.clear()
    relationshipsF8eClient.reset()
  }

  test("Uploading Sealed DDK Data") {
    val result = delegatedDecryptionKeyService().uploadSealedDelegatedDecryptionKeyData(
      fullAccountId = FullAccountMock.accountId,
      f8eEnvironment = FullAccountMock.config.f8eEnvironment,
      sealedData = fakeSealedData
    )

    result.isOk.shouldBeTrue()
  }

  test("Getting Sealed DDK Data") {
    val result = delegatedDecryptionKeyService().getSealedDelegatedDecryptionKeyData()

    result.isOk.shouldBeTrue()
    result.get().shouldBe(
      relationshipsF8eClient.ddkReturnData(
        accountId = FullAccountMock.accountId,
        f8eEnvironment = FullAccountMock.config.f8eEnvironment
      )
    )
  }

  test("Getting Sealed DDK Data Should Fail With Lite Account") {
    accountService.setActiveAccount(LiteAccountMock)
    val result = delegatedDecryptionKeyService().getSealedDelegatedDecryptionKeyData()

    result.isOk.shouldBeFalse()
  }

  test("Getting Sealed DDK Data Should Respect Account ID and F8e Parameters") {
    accountService.setActiveAccount(LiteAccountMock)
    val accountId = FullAccountId("fake-account-id")
    val f8eEnvironment = F8eEnvironment.Development

    val result = delegatedDecryptionKeyService().getSealedDelegatedDecryptionKeyData(
      accountId = accountId,
      f8eEnvironment = f8eEnvironment
    )

    result.isOk.shouldBeTrue()
    result.get().shouldBe(
      relationshipsF8eClient.ddkReturnData(
        accountId = accountId,
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
