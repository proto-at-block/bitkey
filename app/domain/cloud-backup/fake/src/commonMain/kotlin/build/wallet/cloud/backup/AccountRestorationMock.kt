package build.wallet.cloud.backup

import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment.Development

val AccountRestorationMock = AccountRestoration(
  activeSpendingKeyset = SpendingKeysetMock,
  keysets = listOf(SpendingKeysetMock),
  activeAppKeyBundle = AppKeyBundle(
    localId = "fake-uuid",
    spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "spending-dpub")),
    authKey = PublicKey("auth-dpub"),
    networkType = TESTNET,
    recoveryAuthKey = AppRecoveryAuthPublicKeyMock
  ),
  activeHwKeyBundle = HwKeyBundleMock,
  config = FullAccountConfig(
    isHardwareFake = false,
    bitcoinNetworkType = SIGNET,
    f8eEnvironment = Development,
    isTestAccount = false,
    isUsingSocRecFakes = false,
    hardwareType = HardwareType.W1
  ),
  cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock,
  appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
)
