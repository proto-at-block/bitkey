package build.wallet.statemachine.data.account.create

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.OnboardConfig

/**
 * Data state machine for managing creation AND activation of a Full Account
 */
interface CreateFullAccountDataStateMachine : StateMachine<CreateFullAccountDataProps, CreateFullAccountData>

/**
 * @property templateFullAccountConfig [FullAccountConfig] to be used for creating account, unless provided by
 * an existing onboarding data .
 * @property onboardConfig Configuration for the onboarding flow. Currently only used for debug
 * purposes.
 * @property onboardingKeybox Persisted onboarding keybox, if any.
 * @property context The context of the account creation, either a brand new account or one being
 * upgraded.
 * @property rollback Rolls the data state back to [ChoosingCreateOrRecoverState].
 */
data class CreateFullAccountDataProps(
  val templateFullAccountConfig: FullAccountConfig,
  val onboardConfig: OnboardConfig,
  val onboardingKeybox: Keybox?,
  val currencyPreferenceData: CurrencyPreferenceData,
  val context: CreateFullAccountContext,
  val rollback: () -> Unit,
)

/**
 * The context in which the Full Account is being created.
 * We either create a Full Account when we are creating a brand new account, or we
 * "create" a Full Account by upgrading an existing Lite Account to a Full Account.
 */
sealed interface CreateFullAccountContext {
  data object NewFullAccount : CreateFullAccountContext

  data class LiteToFullAccountUpgrade(val liteAccount: LiteAccount) : CreateFullAccountContext
}
