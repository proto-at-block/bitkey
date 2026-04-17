package build.wallet.statemachine.dev.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.store.iCloudAccountRepository
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.debug.cloud.CloudBackupStoreType
import build.wallet.debug.cloud.CloudBackupViewer
import build.wallet.debug.cloud.CloudBackupViewerData
import build.wallet.debug.cloud.CloudBackupViewerData.Loaded
import build.wallet.debug.cloud.name
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.DebugMenuBodyModel
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.ButtonAlertModel.ButtonStyle.Destructive
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.launch

/**
 * iOS implementation of [CloudDevOptionsStateMachine].
 *
 * Allows viewing iCloud account information, cloud backup stores, and store entries.
 */
@BitkeyInject(ActivityScope::class)
class CloudDevOptionsStateMachineImpl(
  private val iCloudAccountRepository: iCloudAccountRepository,
  private val cloudBackupViewer: CloudBackupViewer,
) : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    val iCloudAccount = iCloudAccountRepository.currentAccount().get()
    var ubiquityContainerPath: String? by remember { mutableStateOf("Fetching…") }
    var cloudBackupViewerData by remember { mutableStateOf<CloudBackupViewerData?>(null) }
    var cloudBackupViewerLoadError by remember { mutableStateOf<String?>(null) }
    var refreshViewerCounter by remember { mutableStateOf(0) }
    var pendingDeleteEntry by remember { mutableStateOf<PendingDeleteEntry?>(null) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    var expandedEntries by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberStableCoroutineScope()

    LaunchedEffect("ubiquity-container-path") {
      ubiquityContainerPath = iCloudAccountRepository
        .currentUbiquityContainerPath()
        .get() ?: "None"
    }

    LaunchedEffect(refreshViewerCounter, iCloudAccount?.ubiquityIdentityToken) {
      cloudBackupViewer.load().fold(
        success = {
          cloudBackupViewerData = it
          cloudBackupViewerLoadError = null
        },
        failure = {
          cloudBackupViewerData = null
          cloudBackupViewerLoadError = it.message
        }
      )
    }

    val loadedViewerData = cloudBackupViewerData as? Loaded
    val cloudKitFlagText = when {
      cloudBackupViewerLoadError != null -> "Error"
      loadedViewerData == null -> "Loading..."
      loadedViewerData.iosCloudKitBackupEnabled == true -> "ON"
      else -> "OFF"
    }

    val backupViewerGroups = cloudBackupViewerGroups(
      cloudBackupViewerData = cloudBackupViewerData,
      cloudBackupViewerLoadError = cloudBackupViewerLoadError,
      deleteErrorMessage = deleteErrorMessage,
      noCloudAccountTitle = "No iCloud account signed in",
      noCloudAccountSecondaryText = "Sign in to iCloud to view backup keys.",
      expandedEntries = expandedEntries,
      onRefresh = {
        deleteErrorMessage = null
        refreshViewerCounter++
      },
      onDelete = { storeType, key ->
        pendingDeleteEntry = PendingDeleteEntry(storeType = storeType, key = key)
      },
      onToggleExpanded = { entryId ->
        expandedEntries = if (expandedEntries.contains(entryId)) {
          expandedEntries - entryId
        } else {
          expandedEntries + entryId
        }
      },
      extraSummaryItems = listOf(
        ListItemModel(
          title = "iOS CloudKit Backup Flag",
          sideText = cloudKitFlagText,
          secondaryText = "Both Ubiquitous KVS and CloudKit stores are shown below, regardless of this flag."
        )
      )
    )

    return DebugMenuBodyModel(
      title = "Cloud Storage",
      onBack = props.onExit,
      groups = buildImmutableList {
        add(
          ListGroupModel(
            header = "iCloud Account",
            items = immutableListOf(
              ListItemModel(
                title = "Ubiquity Identity Token",
                secondaryText = "Unique iCloud account identity token.",
                sideText = iCloudAccount?.ubiquityIdentityToken.toString()
              ),
              ListItemModel(
                title = "Ubiquity Container Path",
                secondaryText = "iCloud Drive filesystem location.",
                sideText = ubiquityContainerPath
              )
            ),
            style = ListGroupStyle.CARD_GROUP
          )
        )
        addAll(backupViewerGroups)
      },
      alertModel = pendingDeleteEntry?.let { entry ->
        ButtonAlertModel(
          title = "Delete backup entry?",
          subline = "Store: ${entry.storeType.name}\nKey: ${entry.key}",
          onDismiss = { pendingDeleteEntry = null },
          primaryButtonText = "Delete",
          onPrimaryButtonClick = {
            scope.launch {
              deleteErrorMessage = null
              cloudBackupViewer.deleteEntry(
                storeType = entry.storeType,
                key = entry.key
              ).onFailure {
                deleteErrorMessage = it.message ?: "Failed to delete backup entry."
              }
              refreshViewerCounter++
            }
            pendingDeleteEntry = null
          },
          primaryButtonStyle = Destructive,
          secondaryButtonText = "Cancel",
          onSecondaryButtonClick = { pendingDeleteEntry = null }
        )
      }
    )
  }
}

private data class PendingDeleteEntry(
  val storeType: CloudBackupStoreType,
  val key: String,
)
