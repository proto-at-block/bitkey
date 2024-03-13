package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.socrec.TrustedContactFake1
import build.wallet.bitkey.socrec.TrustedContactFake2
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.AppRecoveryAuthKeypairRetrievalError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator
import build.wallet.cloud.backup.v2.FullAccountFieldsCreatorMock
import build.wallet.cloud.backup.v2.FullAccountFieldsMock
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.recovery.socrec.DelegatedDecryptionKeyFake
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlin.collections.set

class FullAccountCloudBackupCreatorImplTests : FunSpec({

  val keybox = KeyboxMock
  val fullAccountFieldsCreator = FullAccountFieldsCreatorMock()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val socRecCrypto = SocRecCryptoFake(appPrivateKeyDao = appPrivateKeyDao)
  val socRecKeysRepository = SocRecKeysRepository(socRecCrypto, SocRecKeysDaoFake())
  val trustedContacts =
    listOf(
      TrustedContactFake1,
      TrustedContactFake2
    )

  val backupCreator =
    FullAccountCloudBackupCreatorImpl(
      appPrivateKeyDao = appPrivateKeyDao,
      fullAccountFieldsCreator = fullAccountFieldsCreator,
      socRecKeysRepository = socRecKeysRepository
    )

  beforeTest {
    appPrivateKeyDao.clear()
    fullAccountFieldsCreator.reset()
    socRecCrypto.reset()
  }

  context("v2") {
    test("success") {
      val recoveryAuthKeypair = AppRecoveryAuthKeypairMock
      appPrivateKeyDao.appAuthKeys[recoveryAuthKeypair.publicKey] = recoveryAuthKeypair.privateKey
      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake,
          trustedContacts = trustedContacts
        )
        .shouldBeEqual(
          Ok(
            CloudBackupV2(
              accountId = FullAccountIdMock.serverId,
              f8eEnvironment = Development,
              isTestAccount = true,
              delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
              fullAccountFields = FullAccountFieldsMock,
              appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
              isUsingSocRecFakes = false,
              bitcoinNetworkType = BitcoinNetworkType.SIGNET
            )
          )
        )
    }

    test("failure - could not create account info") {
      fullAccountFieldsCreator.createResult = Err(FullAccountFieldsCreator.FullAccountFieldsCreationError.PkekRetrievalError())
      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake,
          trustedContacts = trustedContacts
        )
        .shouldBeErrOfType<FullAccountFieldsCreationError>()
    }

    test("failure - could not get private key") {
      val throwable = Throwable("foo")
      appPrivateKeyDao.getAppAuthPrivateKeyErrResult = Err(throwable)
      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake,
          trustedContacts = trustedContacts
        )
        .shouldBeErrOfType<AppRecoveryAuthKeypairRetrievalError>()
    }
  }
})
