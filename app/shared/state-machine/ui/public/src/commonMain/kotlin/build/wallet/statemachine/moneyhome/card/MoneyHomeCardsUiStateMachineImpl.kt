package build.wallet.statemachine.moneyhome.card

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.replacehardware.ReplaceHardwareCardUiStateMachine
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import kotlinx.collections.immutable.toImmutableList

class MoneyHomeCardsUiStateMachineImpl(
  private val deviceUpdateCardUiStateMachine: DeviceUpdateCardUiStateMachine,
  private val gettingStartedCardUiStateMachine: GettingStartedCardUiStateMachine,
  private val hardwareRecoveryStatusCardUiStateMachine: HardwareRecoveryStatusCardUiStateMachine,
  private val recoveryContactCardsUiStateMachine: RecoveryContactCardsUiStateMachine,
  private val replaceHardwareCardUiStateMachine: ReplaceHardwareCardUiStateMachine,
  private val cloudBackupHealthCardUiStateMachine: CloudBackupHealthCardUiStateMachine,
) : MoneyHomeCardsUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeCardsProps): MoneyHomeCardsModel =
    MoneyHomeCardsModel(
      cards = buildImmutableList {
        // Cloud Backup Health warning card if there's an issue with backup
        add(cloudBackupHealthCardUiStateMachine.model(props.cloudBackupHealthCardUiProps))

        add(
          // Only one of: the HW recovery status card, the replace HW card,  or the device update card
          hardwareRecoveryStatusCardUiStateMachine.model(props.hardwareRecoveryStatusCardUiProps)
            ?: replaceHardwareCardUiStateMachine.model(props.replaceHardwareCardUiProps)
            ?: deviceUpdateCardUiStateMachine.model(props.deviceUpdateCardUiProps)
        )

        // Add invitation cards
        recoveryContactCardsUiStateMachine
          .model(
            RecoveryContactCardsUiProps(
              relationships = props.recoveryContactCardsUiProps.relationships,
              onClick = props.recoveryContactCardsUiProps.onClick
            )
          )
          .forEach(::add)

        // Add getting started card.
        add(gettingStartedCardUiStateMachine.model(props.gettingStartedCardUiProps))
      }
        .filterNotNull()
        .toImmutableList()
    )
}
