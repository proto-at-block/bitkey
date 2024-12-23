package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.RotatingAuthKeys
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps

@BitkeyInject(AppScope::class)
class HasActiveFullAccountDataStateMachineImpl(
  private val lostHardwareRecoveryDataStateMachine: LostHardwareRecoveryDataStateMachine,
  private val trustedContactCloudBackupRefresher: TrustedContactCloudBackupRefresher,
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
) : HasActiveFullAccountDataStateMachine {
  @Composable
  override fun model(props: HasActiveFullAccountDataProps): HasActiveFullAccountData {
    /*
     * Only refresh cloud backups if we don't have an active hardware recovery in progress.
     * The CloudBackupRefresher updates the backup whenever a new backup is uploaded or when
     * SocRec relationships change.
     *
     * This prevents race conditions between cloud backup uploads performed
     *  - explicitly by Lost Hardware Recovery using a new but not yet active keybox.
     *  - implicitly by the CloudBackupRefresher using the active but about to be replaced keybox.
     * In the past we had a race (W-7790) that causes the app to upload a cloud backup for about to
     * be replaced keybox (but not yet currently active), resulting in a cloud backup with outdated
     * auth keys.
     *
     * TODO(W-8314): implement a more robust implementation for auto uploading cloud backups.
     */
    if (props.hardwareRecovery == null) {
      LaunchedEffect("refresh cloud backups", props.account) {
        trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessary(
          scope = this,
          props.account
        )
      }
    }

    // Using collectAsState stops and starts each recomposition because the returned flow can differ,
    // so we use produceState directly instead.
    val pendingAuthKeyRotationAttempt by produceState<PendingAuthKeyRotationAttempt?>(
      null,
      "observing pending attempts"
    ) {
      fullAccountAuthKeyRotationService.observePendingKeyRotationAttemptUntilNull()
        .collect { value = it }
    }

    // TODO: We should probably have a third "None" value, so that we can differentiate between
    //  loading and no pending attempt to mitigate any possible screen flashes.
    pendingAuthKeyRotationAttempt?.let {
      return RotatingAuthKeys(
        account = props.account,
        pendingAttempt = it
      )
    }

    val lostHardwareRecoveryData =
      lostHardwareRecoveryDataStateMachine.model(
        props =
          LostHardwareRecoveryProps(
            account = props.account,
            props.hardwareRecovery
          )
      )

    return ActiveFullAccountLoadedData(
      account = props.account,
      lostHardwareRecoveryData = lostHardwareRecoveryData
    )
  }
}
