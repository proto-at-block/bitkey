package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.debug.DebugDataDeletionReport
import build.wallet.debug.DebugDataDeletionService
import build.wallet.debug.DebugDataDeletionTarget
import build.wallet.debug.DebugDataDeletionTarget.ActiveAccountCloudBackup
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppGlobalAuthKey
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppRecoveryAuthKey
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppSpendingKey
import build.wallet.debug.DebugDataDeletionTarget.AllCloudBackupStores
import build.wallet.debug.DebugDataDeletionTarget.AllLocalAppData
import build.wallet.debug.DebugDataDeletionTarget.AllLocalAppPrivateKeys
import build.wallet.debug.DebugDataDeletionTarget.CloudBackupActiveKeyset
import build.wallet.debug.DebugDataDeletionTarget.CloudBackupsInStore
import build.wallet.debug.DebugDataDeletionTarget.CorruptCloudBackup
import build.wallet.debug.DebugDataDeletionTarget.DescriptorBackupVerificationState
import build.wallet.debug.DebugDataDeletionTarget.LocalCsek
import build.wallet.debug.DebugDataDeletionTarget.OnboardingAppKey
import build.wallet.debug.DebugDataDeletionTarget.OnboardingKeyboxMaterial
import build.wallet.debug.DebugDataDeletionTarget.RelationshipsKeys
import build.wallet.debug.displayName
import build.wallet.debug.cloud.availableCloudBackupStoreTypes
import build.wallet.debug.cloud.name
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.errorFormBodyModelWithOptionalErrorData
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@BitkeyInject(ActivityScope::class)
class DebugDataManagementUiStateMachineImpl(
  private val cloudSignInUiStateMachine: CloudSignInUiStateMachine,
  private val debugDataDeletionService: DebugDataDeletionService,
) : DebugDataManagementUiStateMachine {
  @Composable
  override fun model(props: DebugDataManagementProps): BodyModel {
    var selectedTargets by remember(props.screen) {
      mutableStateOf<ImmutableSet<DebugDataDeletionTarget>>(persistentSetOf())
    }
    var pendingConfirmation by remember { mutableStateOf<PendingConfirmation?>(null) }
    var runningTargets by remember { mutableStateOf<List<DebugDataDeletionTarget>?>(null) }
    var cloudLoggedIn by remember { mutableStateOf(false) }
    var resultReport by remember { mutableStateOf<DebugDataDeletionReport?>(null) }
    var collapsedGroups by remember(props.screen) {
      mutableStateOf(props.screen.defaultCollapsedGroups())
    }

    val onToggleGroupCollapse = { header: String ->
      collapsedGroups = if (collapsedGroups.contains(header)) {
        collapsedGroups.remove(header)
      } else {
        collapsedGroups.add(header)
      }
    }

    val targets = runningTargets
    if (targets != null) {
      if (targets.requiresCloudSignIn() && !cloudLoggedIn) {
        return cloudSignInUiStateMachine.model(
          CloudSignInUiProps(
            forceSignOut = true,
            onSignedIn = { cloudLoggedIn = true },
            onSignInFailure = {
              logWarn(throwable = it) { "Failed to sign in to cloud for debug data deletion" }
              runningTargets = null
            },
            eventTrackerContext = CloudEventTrackerScreenIdContext.DEBUG_MENU
          )
        )
      }

      LaunchedEffect(targets, cloudLoggedIn) {
        resultReport = debugDataDeletionService.delete(targets)
        selectedTargets = persistentSetOf()
        runningTargets = null
        cloudLoggedIn = false
      }

      return LoadingBodyModel(
        id = null,
        title = "Deleting selected data..."
      )
    }

    return when (props.screen) {
      DebugDataManagementScreen.ManualKeyDeletion ->
        manualKeyDeletionModel(
          selectedTargets = selectedTargets,
          onToggleTarget = { target ->
            selectedTargets = selectedTargets.toggle(target)
          },
          onDeleteSelected = {
            pendingConfirmation = PendingConfirmation(
              title = "Delete selected data?",
              targets = selectedTargets.toManualDeletionOrder()
            )
          },
          onBack = props.onBack,
          pendingConfirmation = pendingConfirmation,
          onDismissConfirmation = { pendingConfirmation = null },
          onConfirmTargets = { targetsToDelete ->
            runningTargets = targetsToDelete
            pendingConfirmation = null
          },
          resultReport = resultReport,
          onDismissResult = { resultReport = null },
          collapsedGroups = collapsedGroups,
          onToggleGroupCollapse = onToggleGroupCollapse
        )
      DebugDataManagementScreen.RecoveryScenarioPresets ->
        recoveryScenarioPresetsModel(
          onBack = props.onBack,
          pendingConfirmation = pendingConfirmation,
          onPresetSelected = { preset ->
            pendingConfirmation = PendingConfirmation(
              title = "${preset.title}?",
              targets = preset.targets
            )
          },
          onDismissConfirmation = { pendingConfirmation = null },
          onConfirmTargets = { targetsToDelete ->
            runningTargets = targetsToDelete
            pendingConfirmation = null
          },
          resultReport = resultReport,
          onDismissResult = { resultReport = null }
        )
    }
  }

  @Composable
  private fun manualKeyDeletionModel(
    selectedTargets: ImmutableSet<DebugDataDeletionTarget>,
    onToggleTarget: (DebugDataDeletionTarget) -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    pendingConfirmation: PendingConfirmation?,
    onDismissConfirmation: () -> Unit,
    onConfirmTargets: (List<DebugDataDeletionTarget>) -> Unit,
    resultReport: DebugDataDeletionReport?,
    onDismissResult: () -> Unit,
    collapsedGroups: ImmutableSet<String>,
    onToggleGroupCollapse: (String) -> Unit,
  ): DebugMenuBodyModel {
    val manualGroups = manualDeletionGroups(
      selectedTargets = selectedTargets,
      onToggleTarget = onToggleTarget
    )
    val selectedCount = selectedTargets.size

    return DebugMenuBodyModel(
      title = "Manual Key Deletion",
      onBack = onBack,
      groups = buildImmutableList {
        addAll(manualGroups)
        add(
          ListGroupModel(
            items = immutableListOf(
              ListItemModel(
                title = if (selectedCount == 0) "Select data to delete" else "$selectedCount selected",
                secondaryText = "Deletes only the checked debug data.",
                trailingAccessory = ListItemAccessory.ButtonAccessory(
                  ButtonModel(
                    text = "Delete",
                    treatment = ButtonModel.Treatment.PrimaryDestructive,
                    size = ButtonModel.Size.Compact,
                    isEnabled = selectedCount > 0,
                    onClick = StandardClick(onDeleteSelected)
                  )
                )
              )
            ),
            style = ListGroupStyle.CARD_GROUP
          )
        )
      }.toImmutableList(),
      alertModel = pendingConfirmation?.confirmationAlert(
        onDismiss = onDismissConfirmation,
        onConfirmTargets = onConfirmTargets
      ),
      bottomSheetModel = resultReport?.resultSheet(onDismissResult),
      collapsedGroupHeaders = collapsedGroups,
      onToggleGroupCollapse = onToggleGroupCollapse
    )
  }

  private fun manualDeletionGroups(
    selectedTargets: ImmutableSet<DebugDataDeletionTarget>,
    onToggleTarget: (DebugDataDeletionTarget) -> Unit,
  ): List<ListGroupModel> {
    val cloudStoreTargets = availableCloudBackupStoreTypes()
      .map { storeType ->
        ManualDeletionItem(
          target = CloudBackupsInStore(storeType),
          title = "Cloud backups (${storeType.name})",
          secondaryText = "Delete backups only from ${storeType.name}."
        )
      }

    return listOf(
      ListGroupModel(
        header = "Local app keys",
        items = manualItems(
          selectedTargets = selectedTargets,
          onToggleTarget = onToggleTarget,
          items = listOf(
            ManualDeletionItem(AllLocalAppPrivateKeys, "All local app private keys", "Clears every key in the app private key store."),
            ManualDeletionItem(ActiveAppSpendingKey, "Active app spending key", "Removes the private key for the active spending keyset."),
            ManualDeletionItem(ActiveAppGlobalAuthKey, "Active app global auth key", "Removes the private key used for app auth."),
            ManualDeletionItem(ActiveAppRecoveryAuthKey, "Active app recovery auth key", "Removes the private key used for recovery auth.")
          )
        ),
        style = ListGroupStyle.CARD_GROUP_DIVIDER
      ),
      ListGroupModel(
        header = "Cloud backup keys",
        items = manualItems(
          selectedTargets = selectedTargets,
          onToggleTarget = onToggleTarget,
          items = listOf(
            ManualDeletionItem(AllCloudBackupStores, "All cloud backup stores", "Delete backups from every backend available on this platform."),
            ManualDeletionItem(ActiveAccountCloudBackup, "Active account cloud backup", "Delete the active account backup through the cloud backup service.")
          ) + cloudStoreTargets
        ),
        style = ListGroupStyle.CARD_GROUP_DIVIDER
      ),
      ListGroupModel(
        header = "Onboarding keys",
        items = manualItems(
          selectedTargets = selectedTargets,
          onToggleTarget = onToggleTarget,
          items = listOf(
            ManualDeletionItem(OnboardingAppKey, "Onboarding app key", "Deletes the persisted onboarding app key."),
            ManualDeletionItem(OnboardingKeyboxMaterial, "Onboarding keybox material", "Clears onboarding CSEK/SSEK, hardware keys, and step state.")
          )
        ),
        style = ListGroupStyle.CARD_GROUP_DIVIDER
      ),
      ListGroupModel(
        header = "Recovery support keys",
        items = manualItems(
          selectedTargets = selectedTargets,
          onToggleTarget = onToggleTarget,
          items = listOf(
            ManualDeletionItem(LocalCsek, "Local cloud encryption keys", "Clears unsealed CSEKs stored locally."),
            ManualDeletionItem(RelationshipsKeys, "Trusted contact recovery keys", "Clears Social Recovery key material."),
            ManualDeletionItem(DescriptorBackupVerificationState, "Descriptor backup verification state", "Clears cached descriptor backup health state.")
          )
        ),
        style = ListGroupStyle.CARD_GROUP_DIVIDER
      )
    )
  }

  private fun manualItems(
    selectedTargets: ImmutableSet<DebugDataDeletionTarget>,
    onToggleTarget: (DebugDataDeletionTarget) -> Unit,
    items: List<ManualDeletionItem>,
  ) = items
    .map { item ->
      ListItemModel(
        title = item.title,
        secondaryText = item.secondaryText,
        leadingAccessory = ListItemAccessory.CheckboxAccessory(
          isChecked = selectedTargets.contains(item.target),
          onClick = { onToggleTarget(item.target) }
        ),
        onClick = { onToggleTarget(item.target) }
      )
    }
    .toImmutableList()

  @Composable
  private fun recoveryScenarioPresetsModel(
    onBack: () -> Unit,
    pendingConfirmation: PendingConfirmation?,
    onPresetSelected: (RecoveryScenarioPreset) -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmTargets: (List<DebugDataDeletionTarget>) -> Unit,
    resultReport: DebugDataDeletionReport?,
    onDismissResult: () -> Unit,
  ): DebugMenuBodyModel {
    val presets = recoveryScenarioPresets()

    return DebugMenuBodyModel(
      title = "Recovery Scenario Presets",
      onBack = onBack,
      groups = buildImmutableList {
        add(
          ListGroupModel(
            items = presets.map { preset ->
              ListItemModel(
                title = preset.title,
                secondaryText = preset.secondaryText,
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = { onPresetSelected(preset) }
              )
            }.toImmutableList(),
            style = ListGroupStyle.CARD_GROUP_DIVIDER
          )
        )
      }.toImmutableList(),
      alertModel = pendingConfirmation?.confirmationAlert(
        onDismiss = onDismissConfirmation,
        onConfirmTargets = onConfirmTargets
      ),
      bottomSheetModel = resultReport?.resultSheet(onDismissResult)
    )
  }

  private fun recoveryScenarioPresets(): List<RecoveryScenarioPreset> {
    return listOf(
      RecoveryScenarioPreset(
        title = "Lost app",
        secondaryText = "Full local reset; keeps cloud backups in place.",
        targets = listOf(AllLocalAppData)
      ),
      RecoveryScenarioPreset(
        title = "Lost app + cloud",
        secondaryText = "Deletes all cloud backup stores, then performs a full local reset.",
        targets = listOf(AllCloudBackupStores, AllLocalAppData)
      ),
      RecoveryScenarioPreset(
        title = "Lost cloud only",
        secondaryText = "Deletes all cloud backup stores; keeps local app data.",
        targets = listOf(AllCloudBackupStores)
      )
    ) + listOf(
      RecoveryScenarioPreset(
        title = "Bad cloud backup",
        secondaryText = "Corrupts the active cloud backup for repair/error-path testing.",
        targets = listOf(CorruptCloudBackup)
      ),
      RecoveryScenarioPreset(
        title = "Rollback cloud keyset",
        secondaryText = "Deletes the active keyset from the cloud backup.",
        targets = listOf(CloudBackupActiveKeyset)
      )
    )
  }

  private fun PendingConfirmation.confirmationAlert(
    onDismiss: () -> Unit,
    onConfirmTargets: (List<DebugDataDeletionTarget>) -> Unit,
  ) = ButtonAlertModel(
    title = title,
    subline = targets.joinToString(separator = "\n") { "- ${it.displayName}" },
    onDismiss = onDismiss,
    primaryButtonText = "Delete",
    onPrimaryButtonClick = { onConfirmTargets(targets) },
    primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
    secondaryButtonText = "Cancel",
    onSecondaryButtonClick = onDismiss
  )

  private fun DebugDataDeletionReport.resultSheet(onDismiss: () -> Unit) =
    errorFormBodyModelWithOptionalErrorData(
      title = if (succeeded) "Deleted selected data" else "Some items failed",
      subline = StringModel(if (succeeded) {
        deletedTargets.joinToString(separator = "\n") { "Deleted ${it.displayName}" }
      } else {
        failures.joinToString(separator = "\n") { "${it.target.displayName}: ${it.message}" }
      }),
      primaryButton = ButtonDataModel(
        text = "Done",
        onClick = onDismiss
      ),
      eventTrackerScreenId = if (succeeded) {
        DebugMenuEventTrackerScreenId.DEBUG_MENU_DATA_MANAGEMENT_RESULT
      } else {
        DebugMenuEventTrackerScreenId.DEBUG_MENU_ERROR
      },
      errorData = if (succeeded) {
        null
      } else {
        ErrorData(
          segment = DebugAppSegment,
          actionDescription = "Deleting debug data",
          cause = IllegalStateException(
            failures.joinToString(separator = "; ") { "${it.target.displayName}: ${it.message}" }
          )
        )
      }
    ).asSheetModalScreen(onClosed = onDismiss)

  private fun ImmutableSet<DebugDataDeletionTarget>.toManualDeletionOrder() =
    manualDeletionTargetOrder().filter { contains(it) }

  private fun manualDeletionTargetOrder(): List<DebugDataDeletionTarget> =
    listOf(
      AllLocalAppPrivateKeys,
      ActiveAppSpendingKey,
      ActiveAppGlobalAuthKey,
      ActiveAppRecoveryAuthKey,
      AllCloudBackupStores,
      ActiveAccountCloudBackup
    ) +
      availableCloudBackupStoreTypes().map(::CloudBackupsInStore) +
      listOf(
        OnboardingAppKey,
        OnboardingKeyboxMaterial,
        LocalCsek,
        RelationshipsKeys,
        DescriptorBackupVerificationState
      )

  private fun ImmutableSet<DebugDataDeletionTarget>.toggle(
    target: DebugDataDeletionTarget,
  ): ImmutableSet<DebugDataDeletionTarget> {
    if (contains(target)) return (this - target).toImmutableSet()

    return when (target) {
      AllCloudBackupStores -> withoutCloudBackupMutationTargets() + target
      is CloudBackupsInStore -> {
        val selectedStoreTargets = filterIsInstance<CloudBackupsInStore>().toSet()
        withoutCloudBackupSingleTargetMutations() - AllCloudBackupStores + selectedStoreTargets + target
      }
      ActiveAccountCloudBackup,
      CorruptCloudBackup,
      CloudBackupActiveKeyset,
      -> withoutCloudBackupMutationTargets() + target
      else -> this + target
    }.toImmutableSet()
  }

  private fun ImmutableSet<DebugDataDeletionTarget>.withoutCloudBackupMutationTargets() =
    this - filterIsInstance<CloudBackupsInStore>().toSet() -
      setOf(AllCloudBackupStores, ActiveAccountCloudBackup, CorruptCloudBackup, CloudBackupActiveKeyset)

  private fun ImmutableSet<DebugDataDeletionTarget>.withoutCloudBackupSingleTargetMutations() =
    this - setOf(ActiveAccountCloudBackup, CorruptCloudBackup, CloudBackupActiveKeyset)

  private val DebugDataDeletionTarget.requiresCloudAccess: Boolean
    get() =
      when (this) {
        ActiveAccountCloudBackup,
        AllCloudBackupStores,
        is CloudBackupsInStore,
        CorruptCloudBackup,
        CloudBackupActiveKeyset,
        -> true
        else -> false
      }

  private fun List<DebugDataDeletionTarget>.requiresCloudSignIn(): Boolean =
    any { it.requiresCloudAccess }

  private data class ManualDeletionItem(
    val target: DebugDataDeletionTarget,
    val title: String,
    val secondaryText: String,
  )

  private data class RecoveryScenarioPreset(
    val title: String,
    val secondaryText: String,
    val targets: List<DebugDataDeletionTarget>,
  )

  private data class PendingConfirmation(
    val title: String,
    val targets: List<DebugDataDeletionTarget>,
  )

  private fun DebugDataManagementScreen.defaultCollapsedGroups() =
    when (this) {
      DebugDataManagementScreen.ManualKeyDeletion ->
        persistentSetOf(
          "Local app keys",
          "Cloud backup keys",
          "Onboarding keys",
          "Recovery support keys"
        )
      DebugDataManagementScreen.RecoveryScenarioPresets ->
        persistentSetOf()
    }
}

private object DebugAppSegment : AppSegment {
  override val id: String = "Debug"
}
