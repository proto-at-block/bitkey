package build.wallet.cloud.backup.v2

import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.*
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.CloudBackupV3Restorer.CloudBackupV3RestorerError.*
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.relationships.DelegatedDecryptionKeyFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class CloudBackupV3RestorerImplTests : FunSpec({

  val csekDao = CsekDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val relationshipKeysDao = RelationshipsKeysDaoFake()

  val accountRestoration = AccountRestoration(
    activeSpendingKeyset = SpendingKeysetMock,
    keysets = listOf(SpendingKeysetMock),
    activeAppKeyBundle = AppKeyBundleMock.copy(
      localId = "uuid-0"
    ),
    activeHwKeyBundle = HwKeyBundleMock.copy(
      localId = "uuid-1"
    ),
    config = FullAccountConfig(
      bitcoinNetworkType = SIGNET,
      f8eEnvironment = Development,
      isHardwareFake = false,
      isTestAccount = true,
      isUsingSocRecFakes = false,
      hardwareType = HardwareType.W1
    ),
    cloudBackupForLocalStorage = CloudBackupV3WithFullAccountMock,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
  )

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
  }

  val restorer =
    CloudBackupV3RestorerImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao,
      relationshipsKeysDao = relationshipKeysDao,
      uuidGenerator = UuidGeneratorFake(),
      jsonSerializer = JsonSerializer()
    )

  test("test restoration from V3 backup to an AccountRestoration") {
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult =
      Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    val accountRestorationResult = restorer.restore(CloudBackupV3WithFullAccountMock)
    accountRestorationResult.shouldBeOk(accountRestoration)
    appPrivateKeyDao.asymmetricKeys.shouldBeEqual(
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
    relationshipKeysDao.keys.shouldBeEqual(
      mapOf(
        SocRecKeyPurpose.DelegatedDecryption to DelegatedDecryptionKeyFake
      )
    )
  }

  test("test restoration from V3 backup fails with missing PKEK") {
    restorer.restore(CloudBackupV3WithFullAccountMock)
      .shouldBeErrOfType<PkekMissingError>()
  }

  test("test restoration from V3 backup fails with AccountBackupDecodingError") {
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult = "".encodeUtf8()
    restorer.restore(CloudBackupV3WithFullAccountMock)
      .shouldBeErrOfType<AccountBackupDecodingError>()
  }

  test("test restoration from V3 backup fails with AppSpendingKeypairStorageError") {
    val throwable = Throwable("foo")
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult =
      Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    appPrivateKeyDao.storeAppSpendingKeyPairResult = Err(throwable)

    restorer.restore(CloudBackupV3WithFullAccountMock)
      .shouldBeErrOfType<AppSpendingKeypairStorageError>()
      .cause
      .shouldBeEqual(throwable)
  }

  test("test restoration from V3 backup fails with AppAuthKeypairStorageError") {
    val throwable = Throwable("foo")
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult =
      Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    appPrivateKeyDao.storeAppAuthKeyPairResult = Err(throwable)

    restorer.restore(CloudBackupV3WithFullAccountMock)
      .shouldBeErrOfType<AppAuthKeypairStorageError>()
      .cause
      .shouldBeEqual(throwable)
  }
})
