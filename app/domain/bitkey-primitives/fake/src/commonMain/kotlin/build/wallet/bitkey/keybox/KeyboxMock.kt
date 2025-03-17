package build.wallet.bitkey.keybox

import bitkey.account.FullAccountConfig
import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.account.*
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.frost.AppShareDetailsMock

val KeyboxMock =
  Keybox(
    localId = "keybox-fake-id",
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = SpendingKeysetMock,
    activeAppKeyBundle = AppKeyBundleMock,
    activeHwKeyBundle = HwKeyBundleMock,
    inactiveKeysets = emptyImmutableList(),
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    config =
      FullAccountConfig(
        bitcoinNetworkType = SIGNET,
        isHardwareFake = false,
        f8eEnvironment = Development,
        isUsingSocRecFakes = false,
        isTestAccount = true
      )
  )

val KeyboxMock2 =
  Keybox(
    localId = "keybox-fake-id",
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = SpendingKeysetMock2,
    activeAppKeyBundle = AppKeyBundleMock2,
    activeHwKeyBundle = HwKeyBundleMock,
    inactiveKeysets = emptyImmutableList(),
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    config =
      FullAccountConfig(
        bitcoinNetworkType = SIGNET,
        isHardwareFake = false,
        f8eEnvironment = Development,
        isUsingSocRecFakes = true,
        isTestAccount = true
      )
  )

val FullAccountMock =
  FullAccount(
    accountId = FullAccountIdMock,
    config = KeyboxMock.config,
    keybox = KeyboxMock
  )

val LiteAccountConfigMock =
  LiteAccountConfig(
    bitcoinNetworkType = SIGNET,
    f8eEnvironment = Development,
    isTestAccount = true,
    isUsingSocRecFakes = true
  )

val LiteAccountMock =
  LiteAccount(
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

val OnboardingSoftwareAccountMock =
  OnboardingSoftwareAccount(
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

val SoftwareAccountMock =
  SoftwareAccount(
    accountId = SoftwareAccountId("account-id"),
    config = SoftwareAccountConfigMock,
    keybox = SoftwareKeyboxMock
  )
