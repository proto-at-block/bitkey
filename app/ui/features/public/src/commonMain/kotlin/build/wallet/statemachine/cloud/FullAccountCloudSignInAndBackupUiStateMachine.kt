package build.wallet.statemachine.cloud

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that logs into a cloud account, seals and creates keybox backup and saves it to
 * cloud.
 */
interface FullAccountCloudSignInAndBackupUiStateMachine : StateMachine<FullAccountCloudSignInAndBackupProps, ScreenModel>

/**
 * @property keybox the keybox that we want to back up.
 * @property onBackupFailed callback for when customer failed to sign into cloud storage to
 * back up the keybox, or something went wrong on our side.
 * @property onBackupSaved callback for when keybox has been successfully backed up.
 * @property onExistingAppDataFound callback for when an existing cloud backup is found. The
 * cloudBackup passed as an argument will be null if the file could not be parsed, potentially
 * because it was a v1 backup from Beta.
 * @property isSkipCloudBackupInstructions attempt to start backup process without showing the
 * instructions or forcing sign out. This is used when the user has already seen the instructions
 * and we are attempting to re-do the backup. On the off chance that the CSEK is missing, we'll
 * still show the instructions even if this flag is set to true. See
 * https://github.com/squareup/wallet/pull/10171 for context
 */
data class FullAccountCloudSignInAndBackupProps(
  val sealedCsek: SealedCsek?,
  val keybox: Keybox,
  val onBackupFailed: (cause: Throwable?) -> Unit,
  val onBackupSaved: suspend () -> Unit,
  val onExistingAppDataFound: (
    (
      cloudBackup: CloudBackup?,
      proceed: () -> Unit,
    ) -> Unit
  )? = null,
  val presentationStyle: ScreenPresentationStyle,
  val isSkipCloudBackupInstructions: Boolean = false,
  val requireAuthRefreshForCloudBackup: Boolean,
)
