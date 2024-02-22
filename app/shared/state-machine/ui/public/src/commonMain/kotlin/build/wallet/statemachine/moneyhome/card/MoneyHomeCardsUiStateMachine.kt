package build.wallet.statemachine.moneyhome.card

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiProps
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.ReplaceHardwareCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps

/**
 * State Machine which composes card state machines to be rendered in [MoneyHomeStateMachine]
 */
interface MoneyHomeCardsUiStateMachine : StateMachine<MoneyHomeCardsProps, MoneyHomeCardsModel>

data class MoneyHomeCardsProps(
  val cloudBackupHealthCardUiProps: CloudBackupHealthCardUiProps,
  val deviceUpdateCardUiProps: DeviceUpdateCardUiProps,
  val gettingStartedCardUiProps: GettingStartedCardUiProps,
  val hardwareRecoveryStatusCardUiProps: HardwareRecoveryStatusCardUiProps,
  val recoveryContactCardsUiProps: RecoveryContactCardsUiProps,
  val replaceHardwareCardUiProps: ReplaceHardwareCardUiProps,
)
