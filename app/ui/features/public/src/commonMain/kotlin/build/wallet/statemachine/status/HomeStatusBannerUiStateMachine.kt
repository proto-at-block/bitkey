package build.wallet.statemachine.status

import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.status.StatusBannerModel

/**
 * State machine for the status banner shown on Home screens (Money Home and Settings) when
 * the app is in a state of limited functionality (AppFunctionalityStatus.LimitedFunctionality).
 */
interface HomeStatusBannerUiStateMachine : StateMachine<HomeStatusBannerUiProps, StatusBannerModel?>

data class HomeStatusBannerUiProps(
  val bannerContext: BannerContext,
  val onBannerClick: ((BannerType) -> Unit)?,
)

/**
 * Represents the type of banner that can be shown in the status banner.
 */
sealed interface BannerType {
  /** Shown with the app is offline, inactive, in EEK mode, or when the app is unable to reach Bitkey services. */
  data object OfflineStatus : BannerType

  /** Shown when the customer is at risk of losing funds from lack of recovery methods. */
  data object MissingHardware : BannerType

  /** Shown when the customer is at risk of losing funds from lack of cloud backup. */
  data class MissingCloudBackup(
    val problemWithBackup: AppKeyBackupStatus.ProblemWithBackup,
  ) : BannerType

  /** Shown when the customer is at risk of losing funds from lack of EEK. */
  data class MissingEek(val problemWithBackup: EekBackupStatus.ProblemWithBackup) : BannerType

  /** Shown when the customer is at risk of losing funds from lack of primary touchpoint */
  data object MissingCommunication : BannerType
}

enum class BannerContext {
  SecurityHub,
  Home,
}
