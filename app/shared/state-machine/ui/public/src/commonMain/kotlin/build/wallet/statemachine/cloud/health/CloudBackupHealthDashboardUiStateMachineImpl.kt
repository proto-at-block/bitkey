package build.wallet.statemachine.cloud.health

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.health.EakBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGenerator
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import build.wallet.platform.random.UuidGenerator
import build.wallet.platform.sharing.SharingManager
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
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Warning
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// TODO(796): add integration tests
class CloudBackupHealthDashboardUiStateMachineImpl(
  private val uuidGenerator: UuidGenerator,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val repairCloudBackupStateMachine: RepairCloudBackupStateMachine,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyAccessKitPdfGenerator: EmergencyAccessKitPdfGenerator,
  private val sharingManager: SharingManager,
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

      is ViewingDashboardState -> {
        var shareEakUuid: String? by remember { mutableStateOf(null) }
        shareEakUuid?.let {
          // Use UUID to trigger sharing EAK PDF.
          // This is because we can't reliably tell if the Sharing sheet from previous click
          // is still open or not, so we need to trigger a new, unique one on click.
          LaunchedEffect("share-eak-pdf", shareEakUuid) {
            shareEak(props)
          }
        }

        CloudBackupHealthDashboardBodyModel(
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
              shareEakUuid = uuidGenerator.random()
            },
            onBackUpNowClick = when (currentState.eakBackupStatus) {
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
      }

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

  /**
   * Requests OS to show a sharing sheet for the EAK PDF.
   *
   * Allows customer to download or share the PDF.
   */
  private suspend fun shareEak(props: CloudBackupHealthDashboardProps) {
    // Retrieve sealed CSEK from last uploaded Mobile Key backup in order to generate
    // EAK PDF.
    val mobileKeyBackup = cloudBackupDao
      .get(props.account.accountId.serverId)
      .toErrorIfNull { Error("No backup found.") }
      .logFailure { "Error sharing EAK - could not retrieve Mobile Key backup." }
      .get()
      ?: return

    val sealedCsek: SealedCsek? = when (mobileKeyBackup) {
      is CloudBackupV2 -> mobileKeyBackup.fullAccountFields?.sealedHwEncryptionKey
    }

    if (sealedCsek == null) {
      log(LogLevel.Error) { "Error sharing EAK - sealed CSEK missing, cannot generate PDF." }
      return
    }

    emergencyAccessKitPdfGenerator
      .generate(props.account.keybox, sealedCsek)
      .onSuccess { eak ->
        sharingManager.shareData(
          data = eak.pdfData,
          mimeType = MimeType.PDF,
          title = "Emergency Access Kit",
          completion = {}
        )
      }
      .logFailure { "Error sharing EAK - could not generate PDF" }
  }

  @Composable
  private fun mobileKeyBackupStatusCard(
    timeZone: TimeZone,
    cloudStoreName: String,
    status: MobileKeyBackupStatus,
    onBackUpNowClick: Click?,
  ) = CloudBackupHealthStatusCardModel(
    toolbarModel = null,
    headerModel = FormHeaderModel(
      icon = Icon.CloudBackupMobileKey,
      headline = "Mobile Key Backup",
      subline = "Encrypted backup of your mobile key for easy access when you get a new phone.",
      alignment = FormHeaderModel.Alignment.CENTER,
      sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
    ),
    backupStatus = ListItemModel(
      title = when (status) {
        is MobileKeyBackupStatus.Healthy -> "$cloudStoreName backup"
        MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess -> "Problem with $cloudStoreName\naccount access"
        else -> "Problem with Mobile Key\nBackup"
      },
      treatment = ListItemTreatment.PRIMARY,
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
        model =
          IconModel(
            icon = when (status) {
              is MobileKeyBackupStatus.Healthy -> Icon.SmallIconCheckFilled
              else -> Icon.SmallIconWarning
            },
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Transient,
            iconTint = when (status) {
              is MobileKeyBackupStatus.Healthy -> IconTint.Primary
              is MobileKeyBackupStatus.ProblemWithBackup -> IconTint.Foreground
              else ->
                IconTint.Primary
            }
          )
      )
    ),
    backupStatusActionButton = onBackUpNowClick?.let {
      ButtonModel(
        text = "Back up now",
        treatment = Warning,
        size = ButtonModel.Size.Footer,
        onClick = onBackUpNowClick
      )
    },
    type = CloudBackupHealthStatusCardType.MOBILE_KEY_BACKUP
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
      icon = Icon.CloudBackupEmergencyAccessKit,
      headline = "Emergency Access Kit",
      subline = "Ensures you still have access to your wallet if you can’t access the Bitkey App.",
      alignment = FormHeaderModel.Alignment.CENTER,
      sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
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
        model =
          IconModel(
            icon = when (status) {
              is EakBackupStatus.Healthy -> Icon.SmallIconCheckFilled
              else -> Icon.SmallIconWarningFilled
            },
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Transient,
            iconTint = when (status) {
              is EakBackupStatus.Healthy -> IconTint.Primary
              else ->
                IconTint.Foreground
            }
          )
      )
    ),
    backupStatusActionButton = onBackUpNowClick?.let {
      ButtonModel(
        text = "Back up now",
        treatment = Primary,
        size = ButtonModel.Size.Footer,
        onClick = onBackUpNowClick
      )
    },
    type = CloudBackupHealthStatusCardType.EAK_BACKUP
  )

  /**
   * Determine exact [State] based on current state of [MobileKeyBackupStatus] and [EakBackupStatus]
   */
  private fun determineState(): State {
    val mobileKeyBackupStatus = cloudBackupHealthRepository.mobileKeyBackupStatus().value
    val eakBackupStatus = cloudBackupHealthRepository.eakBackupStatus().value

    return if (mobileKeyBackupStatus != null && eakBackupStatus != null) {
      ViewingDashboardState(mobileKeyBackupStatus, eakBackupStatus)
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
