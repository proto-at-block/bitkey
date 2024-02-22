package build.wallet.statemachine.recovery.cloud

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface UnsealCsekAndGetKeyBundleNfcStateMachine : StateMachine<UnsealCsekAndGetKeyBundleNfcProps, ScreenModel>

data class UnsealCsekAndGetKeyBundleNfcProps(
  val isHardwareFake: Boolean,
  val sealedKey: SealedCsek,
  val bitcoinNetworkType: BitcoinNetworkType,
  val onSuccess: (HwKeyBundle) -> Unit,
  val onBack: () -> Unit,
)
