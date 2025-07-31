package build.wallet.cloud.backup.v2

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.*
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.socrec.SocRecKeyPurpose
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.*
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
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

class CloudBackupV2RestorerImplTests : FunSpec({

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
      isUsingSocRecFakes = false
    ),
    cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
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
      relationshipsKeysDao = relationshipKeysDao,
      uuidGenerator = UuidGeneratorFake()
    )

  test("test restoration from backup to an AccountRestoration") {
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult =
      Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    val accountRestorationResult = restorer.restore(CloudBackupV2WithFullAccountMock)
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

  test("test restoration from backup fails with missing PKEK") {
    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<PkekMissingError>()
  }

  test("test restoration from backup fails with AccountBackupDecodingError") {
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult = "".encodeUtf8()
    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<AccountBackupDecodingError>()
  }

  test("test restoration from backup fails with AppSpendingKeypairStorageError") {
    val throwable = Throwable("foo")
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult =
      Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    appPrivateKeyDao.storeAppSpendingKeyPairResult = Err(throwable)

    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<AppSpendingKeypairStorageError>()
      .cause
      .shouldBeEqual(throwable)
  }

  test("test restoration from backup fails with AppAuthKeypairStorageError") {
    val throwable = Throwable("foo")
    csekDao.set(SealedCsekFake, CsekFake)
    symmetricKeyEncryptor.unsealNoMetadataResult =
      Json.encodeToString(FullAccountKeysMock).encodeUtf8()
    appPrivateKeyDao.storeAppAuthKeyPairResult = Err(throwable)

    restorer.restore(CloudBackupV2WithFullAccountMock)
      .shouldBeErrOfType<AppAuthKeypairStorageError>()
      .cause
      .shouldBeEqual(throwable)
  }

  context("backward compatibility tests") {
    test("test restoration from old backup without keysets field succeeds") {
      val oldFullAccountKeysWithoutKeysets = """
        {
            "activeSpendingKeyset": {
                "localId": "spending-public-keyset-fake-id-1",
                "keysetServerId": "f8e-spending-keyset-id",
                "appDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVappdpub/*",
                "hardwareDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQhardwaredpub/*",
                "serverDpub": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2Userverdpub/*",
                "bitcoinNetworkType": "SIGNET"
            },
            "appGlobalAuthKeypair": {
                "publicKey": "app-auth-dpub",
                "privateKeyHex": "6170702d617574682d707269766174652d6b6579"
            },
            "appSpendingKeys": {
                "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVappdpub/*": {
                    "xprv": "xprv123",
                    "mnemonics": "mnemonic123"
                }
            },
            "activeHwSpendingKey": "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQhardwaredpub/*",
            "activeHwAuthKey": "hw-auth-dpub",
            "rotationAppGlobalAuthKeypair": null
        }
      """.trimIndent()

      val result = Json.decodeFromString<FullAccountKeys>(oldFullAccountKeysWithoutKeysets)
      result.activeSpendingKeyset.shouldBeEqual(SpendingKeysetMock)
      result.keysets.shouldBeEqual(emptyList())
    }

    test("test restoration from old backup with inactiveSpendingKeysets field succeeds") {
      val oldFullAccountKeysWithInactiveKeysets = """
        {
            "activeSpendingKeyset": {
                "localId": "${SpendingKeysetMock.localId}",
                "keysetServerId": "${SpendingKeysetMock.f8eSpendingKeyset.keysetId}",
                "appDpub": "${SpendingKeysetMock.appKey.key.dpub}",
                "hardwareDpub": "${SpendingKeysetMock.hardwareKey.key.dpub}",
                "serverDpub": "${SpendingKeysetMock.f8eSpendingKeyset.spendingPublicKey.key.dpub}",
                "bitcoinNetworkType": "${SpendingKeysetMock.networkType}"
            },
            "inactiveSpendingKeysets": [],
            "appGlobalAuthKeypair": {
                "publicKey": "${FullAccountKeysMock.appGlobalAuthKeypair.publicKey.value}",
                "privateKeyHex": "${FullAccountKeysMock.appGlobalAuthKeypair.privateKey.bytes.hex()}"
            },
            "appSpendingKeys": {
                "${AppSpendingPublicKeyMock.key.dpub}": {
                    "xprv": "${AppSpendingPrivateKeyMock.key.xprv}",
                    "mnemonics": "${AppSpendingPrivateKeyMock.key.mnemonic}"
                }
            },
            "activeHwSpendingKey": "${FullAccountKeysMock.activeHwSpendingKey.key.dpub}",
            "activeHwAuthKey": "${FullAccountKeysMock.activeHwAuthKey.pubKey}",
            "rotationAppGlobalAuthKeypair": ${if (FullAccountKeysMock.rotationAppGlobalAuthKeypair != null) "\"${FullAccountKeysMock.rotationAppGlobalAuthKeypair}\"" else "null"}
        }
      """.trimIndent()

      csekDao.set(SealedCsekFake, CsekFake)
      symmetricKeyEncryptor.unsealNoMetadataResult = oldFullAccountKeysWithInactiveKeysets.encodeUtf8()

      val accountRestorationResult = restorer.restore(CloudBackupV2WithFullAccountMock)
      accountRestorationResult.shouldBeOk()

      val restoration = accountRestorationResult.value
      restoration.activeSpendingKeyset.shouldBeEqual(SpendingKeysetMock)
      restoration.keysets.shouldBeEqual(emptyList())
      restoration.activeAppKeyBundle.spendingKey.shouldBeEqual(SpendingKeysetMock.appKey)
      restoration.config.bitcoinNetworkType.shouldBeEqual(SIGNET)
      restoration.config.f8eEnvironment.shouldBeEqual(Development)

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

    test("test restoration from current backup format with keysets field succeeds") {
      val currentFullAccountKeysWithKeysets = """
        {
            "activeSpendingKeyset": {
                "localId": "${SpendingKeysetMock.localId}",
                "keysetServerId": "${SpendingKeysetMock.f8eSpendingKeyset.keysetId}",
                "appDpub": "${SpendingKeysetMock.appKey.key.dpub}",
                "hardwareDpub": "${SpendingKeysetMock.hardwareKey.key.dpub}",
                "serverDpub": "${SpendingKeysetMock.f8eSpendingKeyset.spendingPublicKey.key.dpub}",
                "bitcoinNetworkType": "${SpendingKeysetMock.networkType}"
            },
            "keysets": [
                {
                    "localId": "${SpendingKeysetMock.localId}",
                    "keysetServerId": "${SpendingKeysetMock.f8eSpendingKeyset.keysetId}",
                    "appDpub": "${SpendingKeysetMock.appKey.key.dpub}",
                    "hardwareDpub": "${SpendingKeysetMock.hardwareKey.key.dpub}",
                    "serverDpub": "${SpendingKeysetMock.f8eSpendingKeyset.spendingPublicKey.key.dpub}",
                    "bitcoinNetworkType": "${SpendingKeysetMock.networkType}"
                }
            ],
            "appGlobalAuthKeypair": {
                "publicKey": "${FullAccountKeysMock.appGlobalAuthKeypair.publicKey.value}",
                "privateKeyHex": "${FullAccountKeysMock.appGlobalAuthKeypair.privateKey.bytes.hex()}"
            },
            "appSpendingKeys": {
                "${AppSpendingPublicKeyMock.key.dpub}": {
                    "xprv": "${AppSpendingPrivateKeyMock.key.xprv}",
                    "mnemonics": "${AppSpendingPrivateKeyMock.key.mnemonic}"
                }
            },
            "activeHwSpendingKey": "${FullAccountKeysMock.activeHwSpendingKey.key.dpub}",
            "activeHwAuthKey": "${FullAccountKeysMock.activeHwAuthKey.pubKey}",
            "rotationAppGlobalAuthKeypair": ${if (FullAccountKeysMock.rotationAppGlobalAuthKeypair != null) "\"${FullAccountKeysMock.rotationAppGlobalAuthKeypair}\"" else "null"}
        }
      """.trimIndent()

      csekDao.set(SealedCsekFake, CsekFake)
      symmetricKeyEncryptor.unsealNoMetadataResult = currentFullAccountKeysWithKeysets.encodeUtf8()

      val accountRestorationResult = restorer.restore(CloudBackupV2WithFullAccountMock)
      accountRestorationResult.shouldBeOk()

      val restoration = accountRestorationResult.value
      restoration.activeSpendingKeyset.shouldBeEqual(SpendingKeysetMock)
      restoration.keysets.shouldBeEqual(listOf(SpendingKeysetMock))
      restoration.activeAppKeyBundle.spendingKey.shouldBeEqual(SpendingKeysetMock.appKey)
      restoration.config.bitcoinNetworkType.shouldBeEqual(SIGNET)
      restoration.config.f8eEnvironment.shouldBeEqual(Development)

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
  }
})
