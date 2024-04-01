package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError.AppRecoveryAuthKeypairRetrievalError
import build.wallet.recovery.socrec.DelegatedDecryptionKeyFake
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiteAccountCloudBackupCreatorImplTests : FunSpec({

  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val socRecCrypto = SocRecCryptoFake(appPrivateKeyDao = appPrivateKeyDao)
  val socRecKeysRepository = SocRecKeysRepository(socRecCrypto, SocRecKeysDaoFake())
  val backupCreator =
    LiteAccountCloudBackupCreatorImpl(
      socRecKeysRepository = socRecKeysRepository,
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
