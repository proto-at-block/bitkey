package build.wallet.statemachine.cloud.health

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGenerator
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import build.wallet.platform.random.UuidGenerator
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreenPresenter.State.*
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
import build.wallet.ui.model.icon.*
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

data class CloudBackupHealthDashboardScreen(
  val account: FullAccount,
  override val origin: Screen?,
) : Screen

@BitkeyInject(ActivityScope::class)
class CloudBackupHealthDashboardScreenPresenter(
  private val uuidGenerator: UuidGenerator,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val repairCloudBackupStateMachine: RepairCloudBackupStateMachine,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyAccessKitPdfGenerator: EmergencyAccessKitPdfGenerator,
  private val sharingManager: SharingManager,
) : ScreenPresenter<CloudBackupHealthDashboardScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: CloudBackupHealthDashboardScreen,
  ): ScreenModel {
    var state: State by remember { mutableStateOf(LoadingState) }
    val cloudStoreName = remember { cloudServiceProvider().name }
    val timeZone = remember { timeZoneProvider.current() }

    return when (val currentState = state) {
      is LoadingState -> {
        LaunchedEffect("load-status") {
          // perform sync first
          cloudBackupHealthRepository.performSync(screen.account)
          state = determineState()
        }
        LoadingBodyModel(id = null).asRootScreen()
      }

      is ViewingDashboardState -> {
        var shareEakUuid: String? by remember { mutableStateOf(null) }
        shareEakUuid?.let {
          // Use UUID to trigger sharing EEK PDF.
          // This is because we can't reliably tell if the Sharing sheet from previous click
          // is still open or not, so we need to trigger a new, unique one on click.
          LaunchedEffect("share-EEK-pdf", shareEakUuid) {
            shareEak(screen)
          }
        }

        CloudBackupHealthDashboardBodyModel(
          onBack = {
            if (screen.origin != null) {
              navigator.goTo(screen.origin)
            } else {
              navigator.exit()
            }
          },
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
          eekBackupStatusCard = eekBackupStatusCard(
            timeZone = timeZone,
            cloudStoreName = cloudStoreName,
            status = currentState.eekBackupStatus,
            onShareEakClick = StandardClick {
              shareEakUuid = uuidGenerator.random()
            },
            onBackUpNowClick = when (currentState.eekBackupStatus) {
              // The "Back up now" option for EEK is intentionally only shown
              // when the EKA backup is missing, or the found EEK is invalid.
              is EekBackupStatus.ProblemWithBackup.InvalidBackup,
              is EekBackupStatus.ProblemWithBackup.BackupMissing,
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
            account = screen.account,
            mobileKeyBackupStatus = currentState.mobileKeyBackupStatus,
            presentationStyle = Root,
            onExit = {
              state = LoadingState
            },
            onRepaired = { status ->
              state = ViewingDashboardState(status.mobileKeyBackupStatus, status.eekBackupStatus)
            }
          )
        )

      is State.UploadingEakBackupState ->
        repairCloudBackupStateMachine
          .model(
            RepairMobileKeyBackupProps(
              account = screen.account,
              mobileKeyBackupStatus = MobileKeyBackupStatus.ProblemWithBackup.BackupMissing,
              presentationStyle = Root,
              onExit = {
                state = LoadingState
              },
              onRepaired = { status ->
                state = ViewingDashboardState(status.mobileKeyBackupStatus, status.eekBackupStatus)
              }
            )
          )
    }
  }

  /**
   * Requests OS to show a sharing sheet for the EEK PDF.
   *
   * Allows customer to download or share the PDF.
   */
  private suspend fun shareEak(props: CloudBackupHealthDashboardScreen) {
    // Retrieve sealed CSEK from last uploaded App Key backup in order to generate
    // EEK PDF.
    val mobileKeyBackup = cloudBackupDao
      .get(props.account.accountId.serverId)
      .toErrorIfNull { Error("No backup found.") }
      .logFailure { "Error sharing EEK - could not retrieve App Key backup." }
      .get()
      ?: return

    val sealedCsek: SealedCsek? = when (mobileKeyBackup) {
      is CloudBackupV2 -> mobileKeyBackup.fullAccountFields?.sealedHwEncryptionKey
    }

    if (sealedCsek == null) {
      logError { "Error sharing EEK - sealed CSEK missing, cannot generate PDF." }
      return
    }

    emergencyAccessKitPdfGenerator
      .generate(props.account.keybox, sealedCsek)
      .onSuccess { EEK ->
        sharingManager.shareData(
          data = EEK.pdfData,
          mimeType = MimeType.PDF,
          title = "Emergency Exit Kit",
          completion = {}
        )
      }
      .logFailure { "Error sharing EEK - could not generate PDF" }
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
      iconModel = headerIconModel(Icon.CloudBackupMobileKey),
      headline = "App Key Backup",
      subline = "Encrypted backup of your App Key for easy access when you get a new phone.",
      alignment = FormHeaderModel.Alignment.CENTER,
      sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
    ),
    backupStatus = ListItemModel(
      title = when (status) {
        is MobileKeyBackupStatus.Healthy -> "$cloudStoreName backup"
        MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess -> "Problem with $cloudStoreName\naccount access"
        else -> "Problem with App Key\nBackup"
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
        treatment = Primary,
        size = ButtonModel.Size.Footer,
        onClick = onBackUpNowClick
      )
    },
    type = CloudBackupHealthStatusCardType.MOBILE_KEY_BACKUP
  )

  @Composable
  private fun eekBackupStatusCard(
    timeZone: TimeZone,
    cloudStoreName: String,
    status: EekBackupStatus,
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
      iconModel = headerIconModel(Icon.CloudBackupEmergencyAccessKit),
      headline = "Emergency Exit Kit",
      subline = "Ensures you still have access to your wallet if you can’t access the Bitkey App.",
      alignment = FormHeaderModel.Alignment.CENTER,
      sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
    ),
    backupStatus = ListItemModel(
      title = "$cloudStoreName backup",
      secondaryText = when (status) {
        is EekBackupStatus.Healthy -> {
          // TODO(BKR-877): use and display real date.
          @Suppress("UNUSED_VARIABLE")
          val formattedDate = remember {
            dateTimeFormatter.shortDate(status.lastUploaded.toLocalDateTime(timeZone))
          }
          "Successfully backed up"
        }

        is EekBackupStatus.ProblemWithBackup.BackupMissing -> "No backup found"
        else -> null
      },
      trailingAccessory = ListItemAccessory.IconAccessory(
        model =
          IconModel(
            icon = when (status) {
              is EekBackupStatus.Healthy -> Icon.SmallIconCheckFilled
              else -> Icon.SmallIconWarningFilled
            },
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Transient,
            iconTint = when (status) {
              is EekBackupStatus.Healthy -> IconTint.Primary
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

  private fun headerIconModel(icon: Icon) =
    IconModel(
      icon = icon,
      iconSize = IconSize.Large,
      iconTint = IconTint.Primary,
      iconBackgroundType = IconBackgroundType.Circle(
        circleSize = IconSize.Avatar,
        color = IconBackgroundType.Circle.CircleColor.Primary
      )
    )

  /**
   * Determine exact [State] based on current state of [MobileKeyBackupStatus] and [EekBackupStatus]
   */
  private fun determineState(): State {
    val mobileKeyBackupStatus = cloudBackupHealthRepository.mobileKeyBackupStatus().value
    val eekBackupStatus = cloudBackupHealthRepository.eekBackupStatus().value

    return if (mobileKeyBackupStatus != null && eekBackupStatus != null) {
      ViewingDashboardState(mobileKeyBackupStatus, eekBackupStatus)
    } else {
      LoadingState
    }
  }

  private sealed interface State {
    data object LoadingState : State

    data class ViewingDashboardState(
      val mobileKeyBackupStatus: MobileKeyBackupStatus,
      val eekBackupStatus: EekBackupStatus,
    ) : State

    data class RepairingMobileKeyBackupState(
      val mobileKeyBackupStatus: MobileKeyBackupStatus.ProblemWithBackup,
    ) : State

    data object UploadingEakBackupState : State
  }
}
