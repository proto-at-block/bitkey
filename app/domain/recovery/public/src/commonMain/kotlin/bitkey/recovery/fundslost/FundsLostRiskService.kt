package bitkey.recovery.fundslost

import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.worker.AppWorker
import kotlinx.coroutines.flow.StateFlow

/**
 * Service to check if the customer is at risk of losing funds due to unable to recover their wallet.
 */
interface FundsLostRiskService {
  /**
   * Emits a flow of the current [FundsLostRiskLevel], driven by changes in
   * recovery methods available to the customer.
   */
  fun riskLevel(): StateFlow<FundsLostRiskLevel>
}

/**
 * [AppWorker] which executes on [build.wallet.worker.RunStrategy.Startup] to pull in the information
 * to determine if the customer is at risk of losing funds.
 */
interface FundsLostRiskSyncWorker : AppWorker

sealed interface FundsLostRiskLevel {
  /**
   * The customer is at risk of losing funds due to missing cloud backup, EEK, hardware or critical
   * contact method
   *
   * @param cause the reason the customer is at risk of losing funds
   */
  data class AtRisk(val cause: AtRiskCause) : FundsLostRiskLevel

  /**
   * The customer has met the baseline for wallet protection
   */
  data object Protected : FundsLostRiskLevel
}

/**
 * The reason the customer is at risk of losing funds.
 */
sealed interface AtRiskCause {
  /**
   * The local active spending keyset doesn't match the server's active keyset.
   */
  data object ActiveSpendingKeysetMismatch : AtRiskCause

  /**
   * The customer is missing a hardware device from their account.
   */
  data object MissingHardware : AtRiskCause

  /**
   * The customer is missing a cloud backup.
   *
   * @param problem the problem with the backup
   */
  data class MissingCloudBackup(val problem: AppKeyBackupStatus.ProblemWithBackup) : AtRiskCause

  /**
   * The customer is missing a critical contact method, email or phone number.
   */
  data object MissingContactMethod : AtRiskCause
}
