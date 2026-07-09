package build.wallet.statemachine.dev.cloud

import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.debug.cloud.CloudBackupEntry
import build.wallet.debug.cloud.CloudBackupStoreData
import build.wallet.debug.cloud.CloudBackupViewerData
import build.wallet.debug.cloud.availableCloudBackupStoreTypes
import build.wallet.debug.cloud.name
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CloudBackupViewerPresentationTests : FunSpec({
  test("fake cloud storage setting is editable only with no account") {
    val unknownAccountStatus: Result<AccountStatus, Error>? = null

    Ok(NoAccount).canEditFakeCloudStorageSetting().shouldBe(true)
    Ok(ActiveAccount(FullAccountMock)).canEditFakeCloudStorageSetting().shouldBe(false)
    Err(Error("account status unavailable")).canEditFakeCloudStorageSetting().shouldBe(false)
    unknownAccountStatus.canEditFakeCloudStorageSetting().shouldBe(false)
  }

  test("fake cloud storage switch remains checked but disabled when setting is locked") {
    var changedValue: Boolean? = null

    val switch = fakeCloudStorageDebugGroup(
      isChecked = true,
      isEditable = false,
      onCheckedChange = { changedValue = it }
    ).fakeCloudSwitch()

    switch.checked.shouldBe(true)
    switch.enabled.shouldBe(false)
    switch.onCheckedChange(false)
    changedValue.shouldBe(null)
  }

  test("fake cloud storage switch invokes callback when setting is editable") {
    var changedValue: Boolean? = null

    val switch = fakeCloudStorageDebugGroup(
      isChecked = true,
      isEditable = true,
      onCheckedChange = { changedValue = it }
    ).fakeCloudSwitch()

    switch.enabled.shouldBe(true)
    switch.onCheckedChange(false)
    changedValue.shouldBe(false)
  }

  test("entry expand button toggles the entry") {
    val toggledEntries = mutableListOf<String>()
    val storeType = availableCloudBackupStoreTypes().first()
    val groups = cloudBackupViewerGroups(
      cloudBackupViewerData = CloudBackupViewerData.Loaded(
        iosCloudKitBackupEnabled = null,
        stores = listOf(
          CloudBackupStoreData(
            storeType = storeType,
            entries = listOf(CloudBackupEntry(key = "backup-key", value = """{"foo":"bar"}""")),
            errorMessage = null
          )
        )
      ),
      cloudBackupViewerLoadError = null,
      deleteErrorMessage = null,
      noCloudAccountTitle = "No account",
      noCloudAccountSecondaryText = "Sign in",
      expandedEntries = emptySet(),
      onRefresh = {},
      onDelete = { _, _ -> },
      onToggleExpanded = { toggledEntries += it }
    )

    val entry = groups[1].items.single()
    val expandButton = entry.specialTrailingAccessory
      .shouldBeInstanceOf<ButtonAccessory>()
      .model

    expandButton.text.shouldBe("Expand")
    expandButton.onClick
      .shouldBeInstanceOf<StandardClick>()
      .invoke()

    toggledEntries.shouldContainExactly("${storeType.name}::backup-key")
  }
})

private fun ListGroupModel.fakeCloudSwitch(): SwitchModel =
  ((items.single().trailingAccessory as SwitchAccessory).model)
