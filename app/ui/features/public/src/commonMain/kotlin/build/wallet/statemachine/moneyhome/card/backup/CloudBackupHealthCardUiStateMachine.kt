package build.wallet.statemachine.moneyhome.card.backup

import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * Produces a [CardModel] with a warning and an action button if there is a problem with the mobile
 * key backup. The action button allows the customer to manually fix the problem.
 *
 * Currently only handles App Key Backups. If there is a problem with EEK backups, no card will
 * be shown, as per intentional product decision.
 */
interface CloudBackupHealthCardUiStateMachine :
  StateMachine<CloudBackupHealthCardUiProps, CardModel?>

/**
 * @param onActionClick The action to take when the action button is clicked.
 */
data class CloudBackupHealthCardUiProps(
  val onActionClick: (status: AppKeyBackupStatus.ProblemWithBackup) -> Unit,
)
