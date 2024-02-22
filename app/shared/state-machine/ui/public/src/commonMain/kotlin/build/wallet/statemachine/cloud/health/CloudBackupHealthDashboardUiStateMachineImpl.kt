package build.wallet.statemachine.cloud.health

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.EakBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.isLoaded
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardUiStateMachineImpl.State.LoadingState
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardUiStateMachineImpl.State.RepairingMobileKeyBackupState
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardUiStateMachineImpl.State.ViewingDashboardState
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.ui.model.Click
import build.wallet.ui.model.Click.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Warning
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// TODO(796): add integration tests
class CloudBackupHealthDashboardUiStateMachineImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val repairCloudBackupStateMachine: RepairCloudBackupStateMachine,
) : CloudBackupHealthDashboardUiStateMachine {
  @Composable
  override fun model(props: CloudBackupHealthDashboardProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(LoadingState)
    }
    val cloudStoreName = remember { cloudServiceProvider().name }
    val timeZone = remember { timeZoneProvider.current() }

    return when (val currentState = state) {
      is LoadingState -> {
        LaunchedEffect("load-status") {
          // perform sync first
          cloudBackupHealthRepository.performSync(props.account)
          state = determineState()
        }
        LoadingBodyModel(id = null).asRootScreen()
      }

      is ViewingDashboardState -> CloudBackupHealthDashboardBodyModel(
        onBack = props.onExit,
        mobileKeyBackupStatusCard = mobileKeyBackupStatusCard(
          timeZone = timeZone,
          cloudStoreName = cloudStoreName,
          status = currentState.mobileKeyBackupStatus,
          onBackUpNowClick = when (currentState.mobileKeyBackupStatus) {
            is MobileKeyBackupStatus.ProblemWithBackup -> StandardClick {
              state = RepairingMobileKeyBackupState(currentState.mobileKeyBackupStatus)
            }

            else -> null
          }
        ),
        eakBackupStatusCard = eakBackupStatusCard(
          timeZone = timeZone,
          cloudStoreName = cloudStoreName,
          status = currentState.eakBackupStatus,
          onShareEakClick = StandardClick {
            // TODO(BKR-799): implement EAK share
          },
          onBackUpNowClick =
            when (currentState.eakBackupStatus) {
              // The "Back up now" option for EAK is intentionally only shown
              // when the EKA backup is missing, or the found EAK is invalid.
              is EakBackupStatus.ProblemWithBackup.InvalidBackup,
              is EakBackupStatus.ProblemWithBackup.BackupMissing,
              -> StandardClick {
                state = State.UploadingEakBackupState
              }

              else -> null
            }
        )
      ).asRootScreen()

      is RepairingMobileKeyBackupState ->
        repairCloudBackupStateMachine.model(
          RepairMobileKeyBackupProps(
            account = props.account,
            mobileKeyBackupStatus = currentState.mobileKeyBackupStatus,
            presentationStyle = Root,
            onExit = {
              state = LoadingState
            },
            onRepaired = { status ->
              state = ViewingDashboardState(status.mobileKeyBackupStatus, status.eakBackupStatus)
            }
          )
        )

      is State.UploadingEakBackupState ->
        repairCloudBackupStateMachine
          .model(
            RepairMobileKeyBackupProps(
              account = props.account,
              mobileKeyBackupStatus = MobileKeyBackupStatus.ProblemWithBackup.BackupMissing,
              presentationStyle = Root,
              onExit = {
                state = LoadingState
              },
              onRepaired = { status ->
                state = ViewingDashboardState(status.mobileKeyBackupStatus, status.eakBackupStatus)
              }
            )
          )
    }
  }

  @Composable
  private fun mobileKeyBackupStatusCard(
    timeZone: TimeZone,
    cloudStoreName: String,
    status: MobileKeyBackupStatus,
    onBackUpNowClick: Click?,
  ) = CloudBackupHealthStatusCardModel(
    toolbarModel = ToolbarModel(
      trailingAccessory = IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconInformationFilled,
            iconSize = IconSize.XSmall,
            iconBackgroundType = IconBackgroundType.Transient,
            iconTint = IconTint.On30
          ),
          onClick = StandardClick {
            // TODO: implement
          }
        )
      )
    ),
    headerModel = FormHeaderModel(
      headline = "Mobile Key Backup",
      subline = "Encrypted backup of your mobile key if you ever get a new phone.",
      alignment = FormHeaderModel.Alignment.CENTER
    ),
    backupStatus = ListItemModel(
      title = when (status) {
        is MobileKeyBackupStatus.Healthy -> "$cloudStoreName backup"
        MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess -> "Problem with $cloudStoreName\naccount access"
        else -> "Problem with Mobile Key\nBackup"
      },
      secondaryText = when (status) {
        is MobileKeyBackupStatus.Healthy -> {
          // TODO(BKR-877): use and display real date.
          @Suppress("UNUSED_VARIABLE")
          val formattedDate = remember {
            dateTimeFormatter.shortDate(status.lastUploaded.toLocalDateTime(timeZone))
          }
          "Successfully backed up"
        }

        is MobileKeyBackupStatus.ProblemWithBackup.BackupMissing -> "No backup found"
        else -> null
      },
      trailingAccessory = ListItemAccessory.IconAccessory(
        when (status) {
          is MobileKeyBackupStatus.Healthy -> Icon.LargeIconCheckFilled
          else -> Icon.SmallIconCloud
        }
      )
    ),
    backupStatusActionButton = onBackUpNowClick?.let {
      ButtonModel(
        text = "Back up now",
        treatment = Warning,
        size = ButtonModel.Size.Footer,
        onClick = onBackUpNowClick
      )
    }
  )

  @Composable
  private fun eakBackupStatusCard(
    timeZone: TimeZone,
    cloudStoreName: String,
    status: EakBackupStatus,
    onShareEakClick: Click,
    onBackUpNowClick: Click?,
  ) = CloudBackupHealthStatusCardModel(
    toolbarModel = ToolbarModel(
      trailingAccessory = IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconShare,
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Transient
          ),
          onClick = onShareEakClick
        )
      )
    ),
    headerModel = FormHeaderModel(
      headline = "Emergency Access Kit",
      subline = "Emergency kit to ensure you still have access if you can’t access the Bitkey App.",
      alignment = FormHeaderModel.Alignment.CENTER
    ),
    backupStatus = ListItemModel(
      title = "$cloudStoreName backup",
      secondaryText = when (status) {
        is EakBackupStatus.Healthy -> {
          // TODO(BKR-877): use and display real date.
          @Suppress("UNUSED_VARIABLE")
          val formattedDate = remember {
            dateTimeFormatter.shortDate(status.lastUploaded.toLocalDateTime(timeZone))
          }
          "Successfully backed up"
        }

        is EakBackupStatus.ProblemWithBackup.BackupMissing -> "No backup found"
        else -> null
      },
      trailingAccessory = ListItemAccessory.IconAccessory(
        when (status) {
          is EakBackupStatus.Healthy -> Icon.LargeIconCheckFilled
          else -> Icon.LargeIconWarningFilled
        }
      )
    ),
    backupStatusActionButton = onBackUpNowClick?.let {
      ButtonModel(
        text = "Back up now",
        size = ButtonModel.Size.Footer,
        onClick = onBackUpNowClick
      )
    }
  )

  /**
   * Determine exact [State] based on current state of [MobileKeyBackupStatus] and [EakBackupStatus]
   */
  private fun determineState(): State {
    val mobileKeyBackupStatus = cloudBackupHealthRepository.mobileKeyBackupStatus().value
    val eakBackupStatus = cloudBackupHealthRepository.eakBackupStatus().value

    return if (mobileKeyBackupStatus.isLoaded() && eakBackupStatus.isLoaded()) {
      ViewingDashboardState(mobileKeyBackupStatus.value, eakBackupStatus.value)
    } else {
      LoadingState
    }
  }

  private sealed interface State {
    data object LoadingState : State

    data class ViewingDashboardState(
      val mobileKeyBackupStatus: MobileKeyBackupStatus,
      val eakBackupStatus: EakBackupStatus,
    ) : State

    data class RepairingMobileKeyBackupState(
      val mobileKeyBackupStatus: MobileKeyBackupStatus.ProblemWithBackup,
    ) : State

    data object UploadingEakBackupState : State
  }
}
