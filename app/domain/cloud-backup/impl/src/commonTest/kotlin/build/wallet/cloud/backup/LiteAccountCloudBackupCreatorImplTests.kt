package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError.AppRecoveryAuthKeypairRetrievalError
import build.wallet.relationships.DelegatedDecryptionKeyFake
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiteAccountCloudBackupCreatorImplTests : FunSpec({

  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val relationshipsCrypto = RelationshipsCryptoFake(appPrivateKeyDao = appPrivateKeyDao)
  val relationshipsKeysRepository = RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val backupCreator =
    LiteAccountCloudBackupCreatorImpl(
      relationshipsKeysRepository = relationshipsKeysRepository,
      appPrivateKeyDao = appPrivateKeyDao
    )

  afterTest {
    appPrivateKeyDao.reset()
  }

  test("success") {
    val recoveryAuthKeypair = AppRecoveryAuthKeypairMock
    appPrivateKeyDao.asymmetricKeys[recoveryAuthKeypair.publicKey] = recoveryAuthKeypair.privateKey
    backupCreator
      .create(
        account = LiteAccountMock
      )
      .shouldBe(
        Ok(
          CloudBackupV2(
            accountId = LiteAccountMock.accountId.serverId,
            f8eEnvironment = LiteAccountMock.config.f8eEnvironment,
            isTestAccount = LiteAccountMock.config.isTestAccount,
            delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
            appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
            fullAccountFields = null,
            bitcoinNetworkType = LiteAccountMock.config.bitcoinNetworkType,
            isUsingSocRecFakes = LiteAccountMock.config.isUsingSocRecFakes
          )
        )
      )
  }

  test("failure - recovery key not available") {
    backupCreator
      .create(
        account = LiteAccountMock
      )
      .shouldBeErrOfType<AppRecoveryAuthKeypairRetrievalError>()
  }
})
