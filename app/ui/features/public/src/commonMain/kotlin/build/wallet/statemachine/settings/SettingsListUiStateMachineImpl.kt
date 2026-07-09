package build.wallet.statemachine.settings

import androidx.compose.runtime.*
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.settings.SettingsBodyModel.RowModel
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.*
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlin.reflect.KClass

@BitkeyInject(ActivityScope::class)
class SettingsListUiStateMachineImpl(
  private val appFunctionalityService: AppFunctionalityService,
  private val migrationService: MigrationService,
) : SettingsListUiStateMachine {
  @Composable
  override fun model(props: SettingsListUiProps): SettingsBodyModel {
    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()

    // Check if migration is available by calling resume()
    var isMigrationAvailable by remember { mutableStateOf(false) }
    LaunchedEffect("check-migration-status") {
      migrationService.resume(MigrationType.PrivateWalletMigration)
        .onSuccess { progress ->
          isMigrationAvailable = progress is MigrationProgress.NotStarted
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
          rowTypes = immutableListOf(
            MobilePay::class,
            AppearancePreference::class,
            NotificationPreferences::class,
            RotateAuthKey::class,
            InheritanceManagement::class
          )
        ),
        SettingsSection(
          props = props,
          appFunctionalityStatus = appFunctionalityStatus,
          title = "Security & Recovery",
          rowTypes = immutableListOf(
            TrustedContacts::class
          )
        ).takeIf { props.isLiteAccount },
        SettingsSection(
          props = props,
          appFunctionalityStatus = appFunctionalityStatus,
          title = "Advanced",
          rowTypes = immutableListOfNotNull(
            CustomElectrumServer::class,
            DebugMenu::class,
            UtxoConsolidation::class,
            ExportTools::class,
            PrivateWalletMigration::class.takeIf {
              isMigrationAvailable
            }
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
    // Build the row models based on if the parent wants to show the row for the section
    val rowModels =
      remember(appFunctionalityStatus, rowTypes, props) {
        rowTypes.mapNotNull { rowType ->
          props.supportedRows
            .firstOrNull { rowType.isInstance(it) }
            ?.rowModel(appFunctionalityStatus, props)
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
  ): RowModel {
    val (icon: Icon, title: String) =
      when (this) {
        is MobilePay -> Pair(MobileLimit, "Transfers")
        is AppearancePreference -> Pair(PaintBrush, "Appearance")
        is NotificationPreferences -> Pair(Notification, "Notifications")
        is CustomElectrumServer -> Pair(Electrum, "Custom Electrum Server")
        is ContactUs -> Pair(Message, "Contact Us")
        is HelpCenter -> Pair(Question, "Help Center")
        is TrustedContacts -> Pair(ShieldPerson, "Recovery Contacts")
        is RotateAuthKey -> Pair(Phone, "Mobile Devices")
        is DebugMenu -> Pair(Information, "Debug Menu")
        is UtxoConsolidation -> Pair(Consolidation, "UTXO Consolidation")
        is InheritanceManagement -> Pair(Inheritance, "Inheritance")
        is ExportTools -> Pair(Document, "Exports")
        is PrivateWalletMigration -> Pair(Wallet, "Private Wallet Update")
      }
    val isRowEnabled = isRowEnabled(appFunctionalityStatus)

    val coachmarkLabelModel = when (this) {
      is PrivateWalletMigration -> CoachmarkLabelModel.New
      else -> null
    }

    return RowModel(
      icon = icon,
      title = title,
      isDisabled = !isRowEnabled,
      coachmarkLabelModel = coachmarkLabelModel,
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

  private fun SettingsListRow.isRowEnabled(
    appFunctionalityStatus: AppFunctionalityStatus,
  ): Boolean {
    return when (this) {
      is MobilePay ->
        appFunctionalityStatus.featureStates.mobilePay == Available
      is AppearancePreference ->
        appFunctionalityStatus.featureStates.fiatExchangeRates == Available
      is NotificationPreferences ->
        appFunctionalityStatus.featureStates.notifications == Available
      is CustomElectrumServer ->
        appFunctionalityStatus.featureStates.customElectrumServer == Available
      is TrustedContacts ->
        appFunctionalityStatus.featureStates.securityAndRecovery == Available
      is HelpCenter ->
        appFunctionalityStatus.featureStates.helpCenter == Available
      is RotateAuthKey ->
        appFunctionalityStatus.featureStates.securityAndRecovery == Available
      is ContactUs ->
        appFunctionalityStatus.featureStates.helpCenter == Available
      is DebugMenu -> true
      is ExportTools -> appFunctionalityStatus.featureStates.exportTools == Available
      is UtxoConsolidation -> appFunctionalityStatus.featureStates.utxoConsolidation == Available
      is InheritanceManagement -> appFunctionalityStatus.featureStates.helpCenter == Available
      is PrivateWalletMigration -> appFunctionalityStatus.featureStates.securityAndRecovery == Available
    }
  }
}
