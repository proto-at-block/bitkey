package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.recovery.Recovery
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData

/**
 * Manages the state of the case when we have an active Full Account ready to use.
 */
interface HasActiveFullAccountDataStateMachine : StateMachine<HasActiveFullAccountDataProps, HasActiveFullAccountData>

data class HasActiveFullAccountDataProps(
  val account: FullAccount,
  val hardwareRecovery: Recovery.StillRecovering?,
  val currencyPreferenceData: CurrencyPreferenceData,
) {
  init {
    hardwareRecovery?.let {
      require(it.factorToRecover == Hardware)
    }
  }
}
