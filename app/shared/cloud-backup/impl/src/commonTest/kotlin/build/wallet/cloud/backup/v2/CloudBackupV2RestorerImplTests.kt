package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.AccountBackupDecodingError
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.AppAuthKeypairStorageError
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.AppSpendingKeypairStorageError
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.PkekMissingError
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.encrypt.SymmetricKeyEncryptorMock
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.platform.random.UuidFake
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import build.wallet.recovery.socrec.TrustedContactIdentityKeyFake
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class CloudBackupV2RestorerImplTests : FunSpec({

  val csekDao = CsekDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorMock()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val socRecKeysDao = SocRecKeysDaoFake()

  val accountRestoration =
    AccountRestoration(
      activeSpendingKeyset = SpendingKeysetMock,
      inactiveKeysets = immutableListOf(SpendingKeysetMock2),
      activeKeyBundle =
        AppKeyBundle(
          localId = UuidFake().random(),
          spendingKey = AppSpendingPublicKeyMock,
          authKey = AppGlobalAuthPublicKeyMock,
          networkType = SIGNET,
          recoveryAuthKey = AppRecoveryAuthPublicKeyMock
        ),
      config =
        KeyboxConfig(
          networkType = SIGNET,
          f8eEnvironment = Development,
          isHardwareFake = false,
          isTestAccount = true,
          isUsingSocRecFakes = false
        ),
      cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock
    )

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
  }

  val restorer =
    CloudBackupV2RestorerImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao,
      socRecKeysDao = socRecKeysDao,
      uuid = UuidFake()
    )

  test("test restoration from backup to an AccountRestoration") {
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealResult = Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    val accountRestorationResult = restorer.restore(CloudBackupV2WithFullAccountMock)
    accountRestorationResult.shouldBeEqual(Ok(accountRestoration))
    appPrivateKeyDao.appAuthKeys.shouldBeEqual(
      mapOf(
        AppGlobalAuthPublicKeyMock to AppGlobalAuthPrivateKeyMock,
        AppRecoveryAuthPublicKeyMock to AppRecoveryAuthPrivateKeyMock
      )
    )
    appPrivateKeyDao.appSpendingKeys.shouldBeEqual(
      mapOf(
        AppSpendingPublicKeyMock to AppSpendingPrivateKeyMock
      )
    )
    socRecKeysDao.keys.shouldBeEqual(
      mapOf(
        SocRecKeyPurpose.TrustedContactIdentity to TrustedContactIdentityKeyFake.key
      )
    )
  }

  test("test restoration from backup fails with missing PKEK") {
    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<PkekMissingError>()
  }

  test("test restoration from backup fails with AccountBackupDecodingError") {
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealResult = "".encodeUtf8()
    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<AccountBackupDecodingError>()
  }

  test("test restoration from backup fails with AppSpendingKeypairStorageError") {
    val throwable = Throwable("foo")
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealResult = Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    appPrivateKeyDao.storeAppSpendingKeyPairResult = Err(throwable)

    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<AppSpendingKeypairStorageError>()
      .cause
      .shouldBeEqual(throwable)
  }

  test("test restoration from backup fails with AppAuthKeypairStorageError") {
    val throwable = Throwable("foo")
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealResult = Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    appPrivateKeyDao.storeAppAuthKeyPairResult = Err(throwable)

    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<AppAuthKeypairStorageError>()
      .cause
      .shouldBeEqual(throwable)
  }
})
