package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.Account
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.OnboardConfig
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData

/**
 * Manages state of the case when there is no active keybox present:
 * - there is an ongoing "Lost App" recovery
 * - onboarding of a new keybox is in progress
 */
interface NoActiveAccountDataStateMachine :
  StateMachine<NoActiveAccountDataProps, NoActiveAccountData>

/**
 * @property [templateFullAccountConfig] template [FullAccountConfig] to be used for creating or recovering a
 * keybox matching that config.
 */
data class NoActiveAccountDataProps(
  val templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
  val existingRecovery: StillRecovering?,
  val onboardConfig: OnboardConfig,
  val onAccountCreated: (Account) -> Unit,
)
