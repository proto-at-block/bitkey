package build.wallet.statemachine.dev.cloud

import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.debug.cloud.CloudBackupStoreType
import build.wallet.debug.cloud.CloudBackupViewerData
import build.wallet.debug.cloud.CloudBackupViewerData.Loaded
import build.wallet.debug.cloud.CloudBackupViewerData.NoCloudAccount
import build.wallet.debug.cloud.name
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get

internal fun fakeCloudStorageDebugGroup(
  isChecked: Boolean,
  isEditable: Boolean,
  onCheckedChange: (Boolean) -> Unit,
): ListGroupModel =
  ListGroupModel(
    header = "Debug",
    items = immutableListOf(
      ListItemModel(
        title = "Use fake cloud storage",
        secondaryText = "For test accounts only. Cloud backups and Emergency Exit Kit files use " +
          "local storage while active.",
        trailingAccessory = SwitchAccessory(
          model = SwitchModel(
            checked = isChecked,
            onCheckedChange = { enabled ->
              if (isEditable) {
                onCheckedChange(enabled)
              }
            },
            enabled = isEditable,
            testTag = "fake-cloud-storage"
          )
        )
      )
    ),
    style = CARD_GROUP
  )

internal fun Result<AccountStatus, Error>?.canEditFakeCloudStorageSetting(): Boolean =
  this?.get() == NoAccount

internal fun cloudBackupStoreTitle(
  storeTypeName: String,
  isFake: Boolean = false,
): String {
  if (isFake) {
    return "Cloud Storage (Fake)"
  }

  val title = when (storeTypeName) {
    "Ubiquitous KVS" -> "UbiquitousKeyValueStore"
    else -> storeTypeName
  }
  return title
}

internal fun cloudBackupViewerStatusItem(
  cloudBackupViewerData: CloudBackupViewerData?,
  cloudBackupViewerLoadError: String?,
  noCloudAccountTitle: String,
  noCloudAccountSecondaryText: String,
): ListItemModel? =
  when {
    cloudBackupViewerLoadError != null ->
      ListItemModel(
        title = "Failed to load backup entries",
        secondaryText = cloudBackupViewerLoadError
      )
    cloudBackupViewerData == null ->
      ListItemModel(
        title = "Loading backup entries..."
      )
    cloudBackupViewerData is NoCloudAccount ->
      ListItemModel(
        title = noCloudAccountTitle,
        secondaryText = noCloudAccountSecondaryText
      )
    else -> null
  }

internal fun cloudBackupViewerGroups(
  cloudBackupViewerData: CloudBackupViewerData?,
  cloudBackupViewerLoadError: String?,
  deleteErrorMessage: String?,
  noCloudAccountTitle: String,
  noCloudAccountSecondaryText: String,
  expandedEntries: Set<String>,
  onRefresh: () -> Unit,
  onDelete: (storeType: CloudBackupStoreType, key: String) -> Unit,
  onToggleExpanded: (entryId: String) -> Unit,
  isCloudStoreFake: Boolean = false,
  extraSummaryItems: List<ListItemModel> = emptyList(),
): List<ListGroupModel> {
  val loadedViewerData = cloudBackupViewerData as? Loaded

  val summaryGroup = ListGroupModel(
    header = "Cloud Backups",
    items = buildImmutableList {
      add(
        ListItemModel(
          title = "Refresh",
          trailingAccessory = ButtonAccessory(
            ButtonModel(
              text = "Refresh",
              size = Compact,
              onClick = StandardClick(onRefresh)
            )
          )
        )
      )

      extraSummaryItems.forEach(::add)

      deleteErrorMessage?.let { message ->
        add(
          ListItemModel(
            title = "Delete failed",
            secondaryText = message
          )
        )
      }

      cloudBackupViewerStatusItem(
        cloudBackupViewerData = cloudBackupViewerData,
        cloudBackupViewerLoadError = cloudBackupViewerLoadError,
        noCloudAccountTitle = noCloudAccountTitle,
        noCloudAccountSecondaryText = noCloudAccountSecondaryText
      )?.let(::add)
    },
    style = CARD_GROUP
  )

  return listOf(summaryGroup) +
    cloudBackupStoreGroups(
      loadedViewerData = loadedViewerData,
      expandedEntries = expandedEntries,
      onDelete = onDelete,
      onToggleExpanded = onToggleExpanded,
      isCloudStoreFake = isCloudStoreFake
    )
}

private fun cloudBackupStoreGroups(
  loadedViewerData: Loaded?,
  expandedEntries: Set<String>,
  onDelete: (storeType: CloudBackupStoreType, key: String) -> Unit,
  onToggleExpanded: (entryId: String) -> Unit,
  isCloudStoreFake: Boolean,
): List<ListGroupModel> =
  loadedViewerData
    ?.stores
    ?.map { store ->
      val storeTitle = cloudBackupStoreTitle(
        storeTypeName = store.storeType.name,
        isFake = isCloudStoreFake
      )
      ListGroupModel(
        header = storeTitle,
        items = buildImmutableList {
          store.errorMessage?.let { message ->
            ListItemModel(
              title = "Store error",
              secondaryText = message
            ).run(::add)
          }

          if (store.entries.isEmpty() && store.errorMessage == null) {
            ListItemModel(
              title = "No backup entries found"
            ).run(::add)
          } else {
            store.entries.forEach { entry ->
              val entryId = "${store.storeType.name}::${entry.key}"
              val isExpanded = expandedEntries.contains(entryId)
              val valueText =
                if (isExpanded) {
                  CloudBackupViewerFormatter.prettyValue(entry.value)
                } else {
                  CloudBackupViewerFormatter.previewValue(entry.value)
                }

              ListItemModel(
                title = entry.key,
                secondaryText = valueText,
                specialTrailingAccessory = ButtonAccessory(
                  ButtonModel(
                    text = if (isExpanded) "Collapse" else "Expand",
                    size = Compact,
                    onClick = StandardClick {
                      onToggleExpanded(entryId)
                    }
                  )
                ),
                trailingAccessory = ButtonAccessory(
                  ButtonModel(
                    text = "Delete",
                    treatment = TertiaryDestructive,
                    size = Compact,
                    onClick = StandardClick {
                      onDelete(store.storeType, entry.key)
                    }
                  )
                ),
                onClick = { onToggleExpanded(entryId) }
              ).run(::add)
            }
          }
        },
        style = ListGroupStyle.CARD_GROUP_DIVIDER
      )
    }.orEmpty()
