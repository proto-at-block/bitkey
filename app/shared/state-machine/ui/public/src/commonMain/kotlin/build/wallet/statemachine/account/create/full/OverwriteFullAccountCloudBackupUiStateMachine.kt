package build.wallet.statemachine.account.create.full

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData.OverwriteFullAccountCloudBackupData

/**
 * Handles the case where a full account cloud backup is discovered during the upload cloud backup
 * step of full account onboarding, presenting the user with the option to either overwrite the
 * existing cloud backup or cancel, deleting the onboarding account both locally and on f8e.
 */
interface OverwriteFullAccountCloudBackupUiStateMachine :
  StateMachine<OverwriteFullAccountCloudBackupUiProps, ScreenModel>

data class OverwriteFullAccountCloudBackupUiProps(
  val data: OverwriteFullAccountCloudBackupData,
)
