package build.wallet.cloud.backup

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.LiteAccountIdMock
import build.wallet.cloud.backup.v2.FullAccountFieldsMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.relationships.DelegatedDecryptionKeyFake
import kotlinx.datetime.Instant

val CloudBackupV2WithFullAccountMock = CloudBackupV2(
  accountId = FullAccountIdMock.serverId,
  f8eEnvironment = F8eEnvironment.Development,
  isTestAccount = true,
  delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
  fullAccountFields = FullAccountFieldsMock,
  appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
  isUsingSocRecFakes = false,
  bitcoinNetworkType = BitcoinNetworkType.SIGNET
)

val CloudBackupV2WithLiteAccountMock = CloudBackupV2(
  accountId = LiteAccountIdMock.serverId,
  f8eEnvironment = F8eEnvironment.Development,
  isTestAccount = true,
  delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
  fullAccountFields = null,
  appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
  isUsingSocRecFakes = true,
  bitcoinNetworkType = BitcoinNetworkType.SIGNET
)

val CloudBackupV3WithFullAccountMock = CloudBackupV3(
  accountId = FullAccountIdMock.serverId,
  f8eEnvironment = F8eEnvironment.Development,
  isTestAccount = true,
  delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
  fullAccountFields = FullAccountFieldsMock,
  appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
  isUsingSocRecFakes = false,
  bitcoinNetworkType = BitcoinNetworkType.SIGNET,
  deviceNickname = "Test Device",
  createdAt = Instant.parse("2024-01-01T00:00:00Z")
)

val CloudBackupV3WithLiteAccountMock = CloudBackupV3(
  accountId = LiteAccountIdMock.serverId,
  f8eEnvironment = F8eEnvironment.Development,
  isTestAccount = true,
  delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
  fullAccountFields = null,
  appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
  isUsingSocRecFakes = true,
  bitcoinNetworkType = BitcoinNetworkType.SIGNET,
  deviceNickname = "Lite Test Device",
  createdAt = Instant.parse("2024-01-01T00:00:00Z")
)

const val CLOUD_BACKUP_V2_WITH_FULL_ACCOUNT_FIELDS_JSON = """
  {
    "accountId":"server-id",
    "f8eEnvironment":"Development",
    "isTestAccount":true,
    "delegatedDecryptionKeypair": {
      "publicKey": "02cf4da9af6606b4ba78d3be2d59dcd7813b3dbdeb028e8ab1f64e9ef0fe3d3967",
      "privateKeyHex": "2cc2b48c50aefe53b3974ed91e6b4ea924f9baa8e77bcc2f537f0b02efe86030"
    },
    "appRecoveryAuthKeypair": {
      "publicKey":"app-recovery-auth-dpub",
      "privateKeyHex":"6170702d7265636f766572792d617574682d707269766174652d6b6579"
    },
    "isUsingSocRecFakes":false,
    "bitcoinNetworkType":"SIGNET",
    "fullAccountFields": {
      "sealedHwEncryptionKey":"b8ef0c208d341bf262638a7ecf142bea1234567890abcdef1234567890abcdef",
      "socRecSealedDekMap": {
        "someRelationshipId": "cipherText-1.nonce-1",
        "someOtherRelationshipId": "cipherText-2.nonce-2"
      },
      "isFakeHardware":false,
      "hwFullAccountKeysCiphertext":{
        "ciphertext":"deadbeef",
        "nonce":"abcdef",
        "tag":"123456"
      },
      "socRecSealedFullAccountKeys": "eyJhbGciOiJ1bnJlYWwifQ.Y2lwaGVydGV4dA.bm9uY2U",
      "rotationAppRecoveryAuthKeypair": null,
      "appGlobalAuthKeyHwSignature":"app-global-auth-key-hw-signature"
    }
  }
"""

const val CLOUD_BACKUP_V3_WITH_FULL_ACCOUNT_FIELDS_JSON = """
  {
    "accountId":"server-id",
    "f8eEnvironment":"Development",
    "isTestAccount":true,
    "delegatedDecryptionKeypair": {
      "publicKey": "02cf4da9af6606b4ba78d3be2d59dcd7813b3dbdeb028e8ab1f64e9ef0fe3d3967",
      "privateKeyHex": "2cc2b48c50aefe53b3974ed91e6b4ea924f9baa8e77bcc2f537f0b02efe86030"
    },
    "appRecoveryAuthKeypair": {
      "publicKey":"app-recovery-auth-dpub",
      "privateKeyHex":"6170702d7265636f766572792d617574682d707269766174652d6b6579"
    },
    "isUsingSocRecFakes":false,
    "bitcoinNetworkType":"SIGNET",
    "fullAccountFields": {
      "sealedHwEncryptionKey":"b8ef0c208d341bf262638a7ecf142bea1234567890abcdef1234567890abcdef",
      "socRecSealedDekMap": {
        "someRelationshipId": "cipherText-1.nonce-1",
        "someOtherRelationshipId": "cipherText-2.nonce-2"
      },
      "isFakeHardware":false,
      "hwFullAccountKeysCiphertext":{
        "ciphertext":"deadbeef",
        "nonce":"abcdef",
        "tag":"123456"
      },
      "socRecSealedFullAccountKeys": "eyJhbGciOiJ1bnJlYWwifQ.Y2lwaGVydGV4dA.bm9uY2U",
      "rotationAppRecoveryAuthKeypair": null,
      "appGlobalAuthKeyHwSignature":"app-global-auth-key-hw-signature"
    },
    "deviceNickname":"Test Device",
    "createdAt":"2024-01-01T00:00:00Z"
  }
"""

/**
 * Centralized list of all full account backup mock versions for parameterized testing.
 * Add new versions here to automatically include them in parameterized tests.
 */
val AllFullAccountBackupMocks = listOf(
  CloudBackupV2WithFullAccountMock,
  CloudBackupV3WithFullAccountMock
)

/**
 * Centralized list of all lite account backup mock versions for parameterized testing.
 * Add new versions here to automatically include them in parameterized tests.
 */
val AllLiteAccountBackupMocks = listOf(
  CloudBackupV2WithLiteAccountMock,
  CloudBackupV3WithLiteAccountMock
)
