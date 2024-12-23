package build.wallet.statemachine.moneyhome.card

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.buildImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiStateMachine
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class MoneyHomeCardsUiStateMachineImpl(
  private val deviceUpdateCardUiStateMachine: DeviceUpdateCardUiStateMachine,
  private val gettingStartedCardUiStateMachine: GettingStartedCardUiStateMachine,
  private val hardwareRecoveryStatusCardUiStateMachine: HardwareRecoveryStatusCardUiStateMachine,
  private val recoveryContactCardsUiStateMachine: RecoveryContactCardsUiStateMachine,
  private val setupHardwareCardUiStateMachine: SetupHardwareCardUiStateMachine,
  private val cloudBackupHealthCardUiStateMachine: CloudBackupHealthCardUiStateMachine,
  private val startSweepCardUiStateMachine: StartSweepCardUiStateMachine,
  private val bitcoinPriceCardUiStateMachine: BitcoinPriceCardUiStateMachine,
  private val inheritanceCardUiStateMachine: InheritanceCardUiStateMachine,
) : MoneyHomeCardsUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeCardsProps): MoneyHomeCardsModel {
    return MoneyHomeCardsModel(
      cards = buildImmutableList {
        inheritanceCardUiStateMachine.model(props.inheritanceCardUiProps).forEach {
          add(it)
        }

        add(startSweepCardUiStateMachine.model(props.startSweepCardUiProps))

        // Cloud Backup Health warning card if there's an issue with backup
        add(cloudBackupHealthCardUiStateMachine.model(props.cloudBackupHealthCardUiProps))

        add(
          // Only one of: the HW recovery status card, the replace HW card,  or the device update card
          hardwareRecoveryStatusCardUiStateMachine.model(props.hardwareRecoveryStatusCardUiProps)
            ?: setupHardwareCardUiStateMachine.model(props.setupHardwareCardUiProps)
            ?: deviceUpdateCardUiStateMachine.model(props.deviceUpdateCardUiProps)
        )

        add(bitcoinPriceCardUiStateMachine.model(props.bitcoinPriceCardUiProps))

        // Add invitation cards
        recoveryContactCardsUiStateMachine
          .model(props.recoveryContactCardsUiProps)
          .forEach(::add)

        // Add getting started card.
        add(gettingStartedCardUiStateMachine.model(props.gettingStartedCardUiProps))
      }
        .filterNotNull()
        .toImmutableList()
    )
  }
}
