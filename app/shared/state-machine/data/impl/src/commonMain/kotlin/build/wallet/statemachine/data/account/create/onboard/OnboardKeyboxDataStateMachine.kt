package build.wallet.statemachine.data.account.create.onboard

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.OnboardConfig

/**
 * Data state machine for managing onboarding of a Keybox, but not activation!
 *
 * Responsible for backing up the keybox and account to cloud storage and setting
 * up notification touchpoints (i.e. push, sms, and email).
 */
interface OnboardKeyboxDataStateMachine :
  StateMachine<OnboardKeyboxDataProps, CreateFullAccountData.OnboardKeyboxDataFull>

data class OnboardKeyboxDataProps(
  val keybox: Keybox,
  val onboardConfig: OnboardConfig,
  val currencyPreferenceData: CurrencyPreferenceData,
  val onExistingCloudBackupFound: (cloudBackup: CloudBackup, proceed: () -> Unit) -> Unit,
  val isSkipCloudBackupInstructions: Boolean,
)
