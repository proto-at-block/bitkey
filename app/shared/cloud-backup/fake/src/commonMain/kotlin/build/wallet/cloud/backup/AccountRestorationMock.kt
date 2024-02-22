package build.wallet.cloud.backup

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.F8eEnvironment.Development

val AccountRestorationMock =
  AccountRestoration(
    activeSpendingKeyset =
      SpendingKeyset(
        localId = "fake-uuid",
        f8eSpendingKeyset =
          F8eSpendingKeyset(
            keysetId = "spending-public-keyset-server-id",
            spendingPublicKey =
              F8eSpendingPublicKey(
                DescriptorPublicKeyMock(identifier = "server-spending-dpub")
              )
          ),
        networkType = TESTNET,
        appKey =
          AppSpendingPublicKey(
            DescriptorPublicKeyMock(identifier = "app-spending-dpub")
          ),
        hardwareKey =
          HwSpendingPublicKey(
            DescriptorPublicKeyMock(identifier = "hardware-spending-dpub")
          )
      ),
    inactiveKeysets = emptyImmutableList(),
    activeKeyBundle =
      AppKeyBundle(
        localId = "fake-uuid",
        spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "spending-dpub")),
        authKey = AppGlobalAuthPublicKey(Secp256k1PublicKey("auth-dpub")),
        networkType = TESTNET,
        recoveryAuthKey = AppRecoveryAuthPublicKeyMock
      ),
    config =
      KeyboxConfig(
        isHardwareFake = false,
        networkType = SIGNET,
        f8eEnvironment = Development,
        isTestAccount = false,
        isUsingSocRecFakes = false
      ),
    cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock
  )
