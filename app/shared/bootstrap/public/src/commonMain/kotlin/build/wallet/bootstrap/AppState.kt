package build.wallet.bootstrap

import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.SoftwareAccount

/**
 * Represents the initial state of the app. Produced by the [LoadAppService].
 */
sealed interface AppState {
  /**
   * Indicates that there is an active Full Account.
   *
   * @param account the active Full Account.
   * @param pendingAuthKeyRotation indicates if there's a pending auth key rotation attempt.
   * If `null`, means there is no attempt in progress.
   */
  data class HasActiveFullAccount(
    val account: FullAccount,
    val pendingAuthKeyRotation: PendingAuthKeyRotationAttempt?,
  ) : AppState

  /**
   * Indicates there is an active [SoftwareAccount].
   */
  data class HasActiveSoftwareAccount(
    val account: SoftwareAccount,
  ) : AppState

  /**
   * A state to indicate that the app state cannot be determined by [AppState] and the
   * [LoadAppService]. The app state should be the [AppDataStateMachine] instead.
   * This type is temporary while we are migrating app state loading from data state machines to
   * the [LoadAppService].
   *
   * TODO(W-9222): migrate loading app state from the AppDataStateMachine to LoadAppService
   */
  data object Undetermined : AppState
}
