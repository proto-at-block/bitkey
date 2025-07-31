package build.wallet.bitkey.keybox

import bitkey.account.FullAccountConfig
import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.frost.AppShareDetailsMock

val KeyboxMock = Keybox(
  localId = "keybox-fake-id",
  fullAccountId = FullAccountIdMock,
  activeSpendingKeyset = SpendingKeysetMock,
  activeAppKeyBundle = AppKeyBundleMock,
  activeHwKeyBundle = HwKeyBundleMock,
  appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
  keysets = listOf(SpendingKeysetMock),
  canUseKeyboxKeysets = true,
  config = FullAccountConfig(
    bitcoinNetworkType = SIGNET,
    isHardwareFake = false,
    f8eEnvironment = Development,
    isUsingSocRecFakes = false,
    isTestAccount = true
  )
)

val EekKeyboxMock = Keybox(
  localId = "eek-keybox-fake-id",
  fullAccountId = FullAccountId("EEK Recovery, no server ID: eek-keybox-fake-id"),
  activeSpendingKeyset = SpendingKeysetMock,
  appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("EEK Recovery: Invalid key"),
  activeAppKeyBundle = AppKeyBundle(
    localId = "public-key-bundle-fake-id",
    spendingKey = AppSpendingPublicKeyMock,
    authKey = PublicKey("EEK Recovery: Invalid key"),
    networkType = SIGNET,
    recoveryAuthKey = PublicKey("EEK Recovery: Invalid recovery key")
  ),
  activeHwKeyBundle = HwKeyBundle(
    localId = "app-key-bundle-mock",
    spendingKey = HwSpendingPublicKeyMock,
    authKey = HwAuthPublicKey(pubKey = Secp256k1PublicKey("EEK Recovery: Invalid key")),
    networkType = SIGNET
  ),
  keysets = listOf(SpendingKeysetMock),
  canUseKeyboxKeysets = false,
  config = FullAccountConfigMock
)

val KeyboxMock2 = Keybox(
  localId = "keybox-fake-id",
  fullAccountId = FullAccountIdMock,
  activeSpendingKeyset = SpendingKeysetMock2,
  activeAppKeyBundle = AppKeyBundleMock2,
  activeHwKeyBundle = HwKeyBundleMock,
  appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
  keysets = listOf(SpendingKeysetMock2),
  canUseKeyboxKeysets = true,
  config = FullAccountConfig(
    bitcoinNetworkType = SIGNET,
    isHardwareFake = false,
    f8eEnvironment = Development,
    isUsingSocRecFakes = true,
    isTestAccount = true
  )
)

val FullAccountMock = FullAccount(
  accountId = FullAccountIdMock,
  config = KeyboxMock.config,
  keybox = KeyboxMock
)

val LiteAccountConfigMock = LiteAccountConfig(
  bitcoinNetworkType = SIGNET,
  f8eEnvironment = Development,
  isTestAccount = true,
  isUsingSocRecFakes = true
)

val LiteAccountMock = LiteAccount(
  accountId = LiteAccountId("server-id"),
  config = LiteAccountConfigMock,
  recoveryAuthKey = AppRecoveryAuthPublicKeyMock
)

val SoftwareAccountConfigMock = SoftwareAccountConfig(
  bitcoinNetworkType = SIGNET,
  f8eEnvironment = Development,
  isTestAccount = true,
  isUsingSocRecFakes = true
)

val OnboardingSoftwareAccountMock = OnboardingSoftwareAccount(
  accountId = SoftwareAccountId("account-id"),
  config = SoftwareAccountConfigMock,
  appGlobalAuthKey = AppGlobalAuthPublicKeyMock,
  recoveryAuthKey = AppRecoveryAuthPublicKeyMock
)

val SoftwareKeyboxMock = SoftwareKeybox(
  id = "software-keybox-fake-id",
  networkType = SIGNET,
  authKey = AppGlobalAuthPublicKeyMock,
  recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
  shareDetails = AppShareDetailsMock
)

val SoftwareAccountMock = SoftwareAccount(
  accountId = SoftwareAccountId("account-id"),
  config = SoftwareAccountConfigMock,
  keybox = SoftwareKeyboxMock
)
