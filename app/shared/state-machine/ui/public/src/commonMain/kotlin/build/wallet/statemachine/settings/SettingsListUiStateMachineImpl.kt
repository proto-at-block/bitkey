package build.wallet.statemachine.settings

import androidx.compose.runtime.*
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProvider
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.settings.SettingsBodyModel.RowModel
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.*
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlin.reflect.KClass

class SettingsListUiStateMachineImpl(
  private val appFunctionalityStatusProvider: AppFunctionalityStatusProvider,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val coachmarkService: CoachmarkService,
) : SettingsListUiStateMachine {
  @Composable
  override fun model(props: SettingsListUiProps): SettingsBodyModel {
    val appFunctionalityStatus =
      remember {
        appFunctionalityStatusProvider.appFunctionalityStatus(props.f8eEnvironment)
      }.collectAsState(AppFunctionalityStatus.FullFunctionality).value

    return SettingsBodyModel(
      onBack = props.onBack,
      sectionModels =
        immutableListOfNotNull(
          SettingsSection(
            props = props,
            appFunctionalityStatus = appFunctionalityStatus,
            title = "General",
            rowTypes =
              immutableListOf(
                MobilePay::class,
                BitkeyDevice::class,
                CurrencyPreference::class,
                NotificationPreferences::class
              )
          ),
          SettingsSection(
            props = props,
            appFunctionalityStatus = appFunctionalityStatus,
            title = "Security & Recovery",
            rowTypes =
              immutableListOf(
                Biometric::class,
                RotateAuthKey::class,
                CloudBackupHealth::class,
                TrustedContacts::class,
                RecoveryChannels::class
              )
          ),
          SettingsSection(
            props = props,
            appFunctionalityStatus = appFunctionalityStatus,
            title = "Advanced",
            rowTypes =
              immutableListOf(
                CustomElectrumServer::class,
                DebugMenu::class
              )
          ),
          SettingsSection(
            props = props,
            appFunctionalityStatus = appFunctionalityStatus,
            title = "Support",
            rowTypes =
              immutableListOf(
                ContactUs::class,
                HelpCenter::class
              )
          )
        )
    )
  }

  @Composable
  fun SettingsSection(
    props: SettingsListUiProps,
    appFunctionalityStatus: AppFunctionalityStatus,
    title: String,
    @Suppress("UnstableCollections")
    rowTypes: List<KClass<out SettingsListRow>>,
  ): SettingsBodyModel.SectionModel? {
    val mobileKeyBackupStatus by remember {
      cloudBackupHealthRepository.mobileKeyBackupStatus()
    }.collectAsState()

    var coachmarksToDisplay by remember { mutableStateOf(listOf<CoachmarkIdentifier>()) }
    LaunchedEffect("coachmarks") {
      coachmarkService
        .coachmarksToDisplay(
          coachmarkIds = setOf(CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark)
        ).onSuccess {
          coachmarksToDisplay = it
        }
    }
    // Build the row models based on if the parent wants to show the row for the section
    val rowModels =
      remember(appFunctionalityStatus, coachmarksToDisplay, mobileKeyBackupStatus) {
        rowTypes.mapNotNull { rowType ->
          props.supportedRows
            .firstOrNull { rowType.isInstance(it) }
            ?.rowModel(appFunctionalityStatus, coachmarksToDisplay, props, mobileKeyBackupStatus)
        }
      }

    if (rowModels.isEmpty()) return null

    return SettingsBodyModel.SectionModel(
      sectionHeaderTitle = title,
      rowModels = rowModels.toImmutableList()
    )
  }

  private fun SettingsListRow.rowModel(
    appFunctionalityStatus: AppFunctionalityStatus,
    coachmarksToDisplay: List<CoachmarkIdentifier>,
    props: SettingsListUiProps,
    mobileKeyBackupStatus: MobileKeyBackupStatus?,
  ): RowModel {
    val (icon: Icon, title: String) =
      when (this) {
        is MobilePay -> Pair(SmallIconMobileLimit, "Mobile Pay")
        is BitkeyDevice -> Pair(SmallIconBitkey, "Bitkey Device")
        is CurrencyPreference -> Pair(SmallIconCurrency, "Currency Display")
        is NotificationPreferences -> Pair(SmallIconNotification, "Notifications")
        is RecoveryChannels -> Pair(SmallIconRecovery, "Recovery Methods")
        is CustomElectrumServer -> Pair(SmallIconElectrum, "Custom Electrum Server")
        is ContactUs -> Pair(SmallIconAnnouncement, "Contact Us")
        is HelpCenter -> Pair(SmallIconQuestion, "Help Center")
        is TrustedContacts -> Pair(SmallIconShieldPerson, "Trusted Contacts")
        is CloudBackupHealth -> Pair(SmallIconCloud, "Cloud Backup")
        is RotateAuthKey -> Pair(SmallIconPhone, "Mobile Devices")
        is DebugMenu -> Pair(SmallIconInformation, "Debug Menu")
        is Biometric -> Pair(SmallIconLock, "App Security")
      }
    val isRowEnabled = isRowEnabled(appFunctionalityStatus)
    return RowModel(
      icon = icon,
      title = title,
      isDisabled = !isRowEnabled,
      specialTrailingIconModel = getSpecialTrailingIconModel(mobileKeyBackupStatus),
      onClick = {
        if (isRowEnabled) {
          onClick()
        } else {
          when (appFunctionalityStatus) {
            is AppFunctionalityStatus.FullFunctionality -> Unit // Nothing to do
            is AppFunctionalityStatus.LimitedFunctionality ->
              props.onShowAlert(
                AppFunctionalityStatusAlertModel(
                  status = appFunctionalityStatus,
                  onDismiss = props.onDismissAlert
                )
              )
          }
        }
      },
      showNewCoachmark = when (this) {
        is BitkeyDevice ->
          coachmarksToDisplay
            .contains(CoachmarkIdentifier.MultipleFingerprintsCoachmark)
        is Biometric -> coachmarksToDisplay.contains(CoachmarkIdentifier.BiometricUnlockCoachmark)
        else -> false
      }
    )
  }

  private fun SettingsListRow.getSpecialTrailingIconModel(
    mobileKeyBackupStatus: MobileKeyBackupStatus?,
  ): IconModel? {
    return when (this) {
      is CloudBackupHealth -> {
        IconModel(
          icon = Icon.SmallIconInformationFilled,
          iconSize = IconSize.Small,
          iconTint = IconTint.Warning
        ).takeIf {
          mobileKeyBackupStatus != null &&
            mobileKeyBackupStatus is MobileKeyBackupStatus.ProblemWithBackup
        }
      }
      else -> null
    }
  }

  private fun SettingsListRow.isRowEnabled(
    appFunctionalityStatus: AppFunctionalityStatus,
  ): Boolean {
    return when (this) {
      // Rows that are always available
      is BitkeyDevice ->
        true

      is MobilePay ->
        appFunctionalityStatus.featureStates.mobilePay == Available
      is CurrencyPreference ->
        appFunctionalityStatus.featureStates.fiatExchangeRates == Available
      is NotificationPreferences, is RecoveryChannels ->
        appFunctionalityStatus.featureStates.notifications == Available
      is CustomElectrumServer ->
        appFunctionalityStatus.featureStates.customElectrumServer == Available
      is TrustedContacts ->
        appFunctionalityStatus.featureStates.securityAndRecovery == Available
      is HelpCenter ->
        appFunctionalityStatus.featureStates.helpCenter == Available
      is CloudBackupHealth ->
        appFunctionalityStatus.featureStates.cloudBackupHealth == Available
      is RotateAuthKey ->
        appFunctionalityStatus.featureStates.securityAndRecovery == Available
      is ContactUs ->
        appFunctionalityStatus.featureStates.helpCenter == Available
      is DebugMenu -> true
      is Biometric -> true
    }
  }
}
