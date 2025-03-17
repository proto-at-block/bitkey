package build.wallet.bitkey.keybox

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.F8eEnvironment.Development
import kotlin.time.Duration

private val appKeyBundle =
  AppKeyBundle(
    localId = "appKeyBundleid",
    spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "newSpendingXpub")),
    authKey = PublicKey("newAuthFakeXpub"),
    networkType = TESTNET,
    recoveryAuthKey = AppRecoveryAuthPublicKeyMock
  )
private val hwKeyBundle =
  HwKeyBundle(
    localId = "hwKeyBundleid",
    spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "newSpendingXpub")),
    authKey = HwAuthPublicKey(Secp256k1PublicKey("newAuthFakeXpub")),
    networkType = TESTNET
  )
private val config =
  FullAccountConfig(
    bitcoinNetworkType = TESTNET,
    isHardwareFake = true,
    f8eEnvironment = Development,
    isTestAccount = true,
    isUsingSocRecFakes = true,
    delayNotifyDuration = Duration.ZERO
  )

val WithAppKeysMock =
  KeyCrossDraft.WithAppKeys(
    appKeyBundle = appKeyBundle,
    config = config
  )

val WithAppKeysAndHardwareKeysMock =
  WithAppKeysAndHardwareKeys(
    appKeyBundle = appKeyBundle,
    hardwareKeyBundle = hwKeyBundle,
    config = config,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
  )
