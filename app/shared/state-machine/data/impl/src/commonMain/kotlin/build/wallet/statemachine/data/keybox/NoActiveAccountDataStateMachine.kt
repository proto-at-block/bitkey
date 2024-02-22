package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.OnboardConfigData.LoadedOnboardConfigData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData

/**
 * Manages state of the case when there is no active keybox present:
 * - there is an ongoing "Lost App" recovery
 * - onboarding of a new keybox is in progress
 */
interface NoActiveAccountDataStateMachine : StateMachine<NoActiveAccountDataProps, NoActiveAccountData>

/**
 * @property [templateKeyboxConfig] template [KeyboxConfig] to be used for creating or recovering a
 * keybox matching that config.
 */
data class NoActiveAccountDataProps(
  val templateKeyboxConfigData: LoadedTemplateKeyboxConfigData,
  val existingRecovery: StillRecovering?,
  val currencyPreferenceData: CurrencyPreferenceData,
  val newAccountOnboardConfigData: LoadedOnboardConfigData,
  val onAccountCreated: (Account) -> Unit,
)
