package build.wallet.statemachine.data.account.create.onboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDao
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.BackingUpKeyboxToCloudDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingNotificationsDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.FailedCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.SettingNotificationsPreferencesDataFull
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess

class OnboardKeyboxDataStateMachineImpl(
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
) : OnboardKeyboxDataStateMachine {
  @Composable
  override fun model(props: OnboardKeyboxDataProps): OnboardKeyboxDataFull {
    val cloudBackupState =
      rememberCloudBackupState()
        ?: return OnboardKeyboxDataFull.LoadingInitialStepDataFull

    val notificationPreferencesState =
      rememberNotificationPreferencesState()
        ?: return OnboardKeyboxDataFull.LoadingInitialStepDataFull

    // Steps are: CloudBackup, NotificationPreferences.
    // Check for Incomplete in that order.
    when (cloudBackupState) {
      Incomplete ->
        return CloudBackupData(props)
      Complete ->
        Unit
    }

    return when (notificationPreferencesState) {
      Incomplete ->
        NotificationPreferencesData(props)
      Complete ->
        CompletingNotificationsDataFull
    }
  }

  @Composable
  private fun CloudBackupData(props: OnboardKeyboxDataProps): OnboardKeyboxDataFull {
    // Wait until sealed CSEK result is loaded from the database
    val sealedCsekResult =
      rememberSealedCsek()
        ?: return OnboardKeyboxDataFull.LoadingInitialStepDataFull

    // Local variables to keep track of state
    var stepIsCompleting by remember {
      // Start off completing if the CloudBackup step is configured to be skipped
      mutableStateOf(props.onboardConfig.stepsToSkip.contains(CloudBackup))
    }
    var stepDidFail: Error? by remember { mutableStateOf(null) }

    return if (stepIsCompleting) {
      CompletingCloudBackupData {
        stepIsCompleting = false
      }
    } else if (stepDidFail != null) {
      FailedCloudBackupDataFull(
        error = stepDidFail!!,
        retry = { stepDidFail = null }
      )
    } else {
      val sealedCsek = sealedCsekResult.get()
      BackingUpKeyboxToCloudDataFull(
        keybox = props.keybox,
        sealedCsek = sealedCsek,
        onBackupFailed = { stepDidFail = Error(it) },
        onBackupSaved = { stepIsCompleting = true },
        onExistingAppDataFound = props.onExistingAppDataFound,
        isSkipCloudBackupInstructions = props.isSkipCloudBackupInstructions
      )
    }
  }

  @Composable
  private fun CompletingCloudBackupData(onSuccess: () -> Unit): OnboardKeyboxDataFull {
    LaunchedEffect("complete-backup-step") {
      onboardingKeyboxStepStateDao
        .setStateForStep(CloudBackup, Complete)
        .onSuccess {
          // Now that the backup is complete, we don't need the sealed csek anymore
          onboardingKeyboxSealedCsekDao
            .clear()
            .onSuccess {
              onSuccess()
            }
        }
    }
    return CompletingCloudBackupDataFull
  }

  @Composable
  private fun NotificationPreferencesData(props: OnboardKeyboxDataProps): OnboardKeyboxDataFull {
    // Local variables to keep track of state
    var stepIsCompleting by remember {
      // Start off completing if the NotificationPreferences step is configured to be skipped
      mutableStateOf(props.onboardConfig.stepsToSkip.contains(NotificationPreferences))
    }

    return if (stepIsCompleting) {
      LaunchedEffect("complete-notifications-step") {
        onboardingKeyboxStepStateDao
          .setStateForStep(NotificationPreferences, Complete)
          .onSuccess {
            stepIsCompleting = false
          }
      }
      CompletingNotificationsDataFull
    } else {
      SettingNotificationsPreferencesDataFull(
        keybox = props.keybox,
        onComplete = { stepIsCompleting = true }
      )
    }
  }

  @Composable
  private fun rememberCloudBackupState(): OnboardingKeyboxStepState? {
    return remember {
      onboardingKeyboxStepStateDao.stateForStep(CloudBackup)
    }.collectAsState(null).value
  }

  @Composable
  private fun rememberNotificationPreferencesState(): OnboardingKeyboxStepState? {
    return remember {
      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences)
    }.collectAsState(null).value
  }

  @Composable
  private fun rememberSealedCsek(): Result<SealedCsek?, Throwable>? {
    return produceState<Result<SealedCsek?, Throwable>?>(initialValue = null) {
      value = onboardingKeyboxSealedCsekDao.get()
    }.value
  }
}
