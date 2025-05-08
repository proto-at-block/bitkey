package build.wallet.statemachine.settings

import androidx.compose.runtime.*
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.SecurityHubFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.settings.SettingsBodyModel.RowModel
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.*
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

@BitkeyInject(ActivityScope::class)
class SettingsListUiStateMachineImpl(
  private val appFunctionalityService: AppFunctionalityService,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val coachmarkService: CoachmarkService,
  private val securityHubFeatureFlag: SecurityHubFeatureFlag,
) : SettingsListUiStateMachine {
  @Composable
  override fun model(props: SettingsListUiProps): SettingsBodyModel {
    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()
    val scope = rememberStableCoroutineScope()

    var coachmarksToDisplay by remember { mutableStateOf(immutableListOf<CoachmarkIdentifier>()) }
    LaunchedEffect("coachmarks") {
      coachmarkService.coachmarksToDisplay(
        coachmarkIds = setOf(
          CoachmarkIdentifier.SecurityHubSettingsCoachmark
        )
      ).onSuccess {
        coachmarksToDisplay = it.toImmutableList()
      }
    }

    return SettingsBodyModel(
      onBack = props.onBack,
      toolbarModel = ToolbarModel(
        leadingAccessory = BackAccessory(onClick = props.onBack),
        middleAccessory = ToolbarMiddleAccessoryModel(title = "Settings")
      ),
      sectionModels = immutableListOfNotNull(
        SettingsSection(
          props = props,
          appFunctionalityStatus = appFunctionalityStatus,
          title = "General",
          rowTypes = if (securityHubFeatureFlag.isEnabled()) {
            immutableListOf(
              MobilePay::class,
              AppearancePreference::class,
              NotificationPreferences::class,
              RotateAuthKey::class,
              InheritanceManagement::class
            )
          } else {
            immutableListOf(
              MobilePay::class,
              BitkeyDevice::class,
              AppearancePreference::class,
              NotificationPreferences::class
            )
          }
        ),
        SettingsSection(
          props = props,
          appFunctionalityStatus = appFunctionalityStatus,
          title = "Security & Recovery",
          rowTypes = immutableListOf(
            Biometric::class,
            InheritanceManagement::class,
            RotateAuthKey::class,
            CloudBackupHealth::class,
            CriticalAlerts::class,
            TrustedContacts::class
          )
        ).takeIf { !securityHubFeatureFlag.isEnabled() },
        SettingsSection(
          props = props,
          appFunctionalityStatus = appFunctionalityStatus,
          title = "Advanced",
          rowTypes = immutableListOf(
            CustomElectrumServer::class,
            DebugMenu::class,
            UtxoConsolidation::class,
            ExportTools::class
          )
        ),
        SettingsSection(
          props = props,
          appFunctionalityStatus = appFunctionalityStatus,
          title = "Support",
          rowTypes = immutableListOf(
            ContactUs::class,
            HelpCenter::class
          )
        )
      ),
      onSecurityHubCoachmarkClick = {
        scope.launch {
          coachmarkService.markCoachmarkAsDisplayed(CoachmarkIdentifier.SecurityHubSettingsCoachmark)
        }
        props.goToSecurityHub()
      }.takeIf { coachmarksToDisplay.contains(CoachmarkIdentifier.SecurityHubSettingsCoachmark) }
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

    // Build the row models based on if the parent wants to show the row for the section
    val rowModels =
      remember(appFunctionalityStatus, mobileKeyBackupStatus) {
        rowTypes.mapNotNull { rowType ->
          props.supportedRows
            .firstOrNull { rowType.isInstance(it) }
            ?.rowModel(appFunctionalityStatus, props, mobileKeyBackupStatus)
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
    props: SettingsListUiProps,
    mobileKeyBackupStatus: MobileKeyBackupStatus?,
  ): RowModel {
    val (icon: Icon, title: String) =
      when (this) {
        is MobilePay -> Pair(SmallIconMobileLimit, "Transfers")
        is BitkeyDevice -> Pair(SmallIconBitkey, "Bitkey Device")
        is AppearancePreference -> Pair(SmallIconPaintBrush, "Appearance")
        is NotificationPreferences -> Pair(SmallIconNotification, "Notifications")
        is CriticalAlerts -> Pair(SmallIconWarning, "Critical Alerts")
        is CustomElectrumServer -> Pair(SmallIconElectrum, "Custom Electrum Server")
        is ContactUs -> Pair(SmallIconMessage, "Contact Us")
        is HelpCenter -> Pair(SmallIconQuestion, "Help Center")
        is TrustedContacts -> Pair(SmallIconShieldPerson, "Trusted Contacts")
        is CloudBackupHealth -> Pair(SmallIconCloud, "Cloud Backup")
        is RotateAuthKey -> Pair(SmallIconPhone, "Mobile Devices")
        is DebugMenu -> Pair(SmallIconInformation, "Debug Menu")
        is Biometric -> Pair(SmallIconLock, "App Security")
        is UtxoConsolidation -> Pair(SmallIconConsolidation, "UTXO Consolidation")
        is InheritanceManagement -> Pair(SmallIconInheritance, "Inheritance")
        is ExportTools -> Pair(SmallIconDocument, "Exports")
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
      }
    )
  }

  private fun SettingsListRow.getSpecialTrailingIconModel(
    mobileKeyBackupStatus: MobileKeyBackupStatus?,
  ): IconModel? {
    return when (this) {
      is CloudBackupHealth -> {
        IconModel(
          icon = SmallIconInformationFilled,
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
      is AppearancePreference ->
        appFunctionalityStatus.featureStates.fiatExchangeRates == Available
      is NotificationPreferences, is CriticalAlerts ->
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
      is ExportTools -> appFunctionalityStatus.featureStates.exportTools == Available
      is UtxoConsolidation -> appFunctionalityStatus.featureStates.utxoConsolidation == Available
      is InheritanceManagement -> appFunctionalityStatus.featureStates.helpCenter == Available
    }
  }
}
