package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake2
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.*
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator
import build.wallet.cloud.backup.v2.FullAccountFieldsCreatorMock
import build.wallet.cloud.backup.v2.FullAccountFieldsMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.relationships.*
import build.wallet.testing.shouldBeErrOfType
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlin.collections.set

class FullAccountCloudBackupCreatorImplTests : FunSpec({

  val clock = ClockFake()
  val keybox = KeyboxMock
  val fullAccountFieldsCreator = FullAccountFieldsCreatorMock()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val relationshipsCrypto = RelationshipsCryptoFake(appPrivateKeyDao = appPrivateKeyDao)
  val relationshipsKeysRepository = RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val trustedContacts =
    listOf(
      EndorsedTrustedContactFake1,
      EndorsedTrustedContactFake2
    )
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)

  val backupCreator =
    FullAccountCloudBackupCreatorImpl(
      appPrivateKeyDao = appPrivateKeyDao,
      fullAccountFieldsCreator = fullAccountFieldsCreator,
      relationshipsKeysRepository = relationshipsKeysRepository,
      relationshipsService = relationshipsService
    )

  beforeTest {
    appPrivateKeyDao.clear()
    fullAccountFieldsCreator.reset()
    relationshipsCrypto.reset()
    relationshipsService.clear()
  }

  context("v2") {
    test("success") {
      val recoveryAuthKeypair = AppRecoveryAuthKeypairMock
      appPrivateKeyDao.asymmetricKeys[recoveryAuthKeypair.publicKey] =
        recoveryAuthKeypair.privateKey
      val relationships = RelationshipsFake.copy(endorsedTrustedContacts = trustedContacts)
      relationshipsService.syncAndVerifyRelationshipsResult = Ok(relationships)

      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake
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

      relationshipsService.syncCalls.awaitItem()
    }

    test("failure - could not create account info") {
      fullAccountFieldsCreator.createResult =
        Err(FullAccountFieldsCreator.FullAccountFieldsCreationError.PkekRetrievalError(Error("test")))
      val relationships = RelationshipsFake.copy(endorsedTrustedContacts = trustedContacts)
      relationshipsService.syncAndVerifyRelationshipsResult = Ok(relationships)

      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake
        )
        .shouldBeErrOfType<FullAccountFieldsCreationError>()

      relationshipsService.syncCalls.awaitItem()
    }

    test("failure - could not get private key") {
      val throwable = Throwable("foo")
      val relationships = RelationshipsFake.copy(endorsedTrustedContacts = trustedContacts)
      relationshipsService.syncAndVerifyRelationshipsResult = Ok(relationships)

      appPrivateKeyDao.getAppPrivateKeyErrResult = Err(throwable)
      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake
        )
        .shouldBeErrOfType<AppRecoveryAuthKeypairRetrievalError>()

      relationshipsService.syncCalls.awaitItem()
    }

    test("failure - could not verify endorsed Trusted Contacts") {
      relationshipsService.syncAndVerifyRelationshipsResult = Err(Error("oops"))

      backupCreator
        .create(
          keybox = keybox,
          sealedCsek = SealedCsekFake
        )
        .shouldBeErrOfType<SocRecVerificationError>()

      relationshipsService.syncCalls.awaitItem()
    }
  }
})
