package build.wallet.ui.model.list

import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemAccessory.CircularCharacterAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemAccessory.TextAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment.CENTER
import build.wallet.ui.model.switch.SwitchModel

data class ListItemModel(
  val title: String,
  val titleAlignment: ListItemTitleAlignment = ListItemTitleAlignment.LEFT,
  val listItemTitleBackgroundTreatment: ListItemTitleBackgroundTreatment? = null,
  val secondaryText: String? = null,
  val sideText: String? = null,
  val secondarySideText: String? = null,
  val leadingAccessoryAlignment: ListItemAccessoryAlignment = CENTER,
  val leadingAccessory: ListItemAccessory? = null,
  val trailingAccessory: ListItemAccessory? = null,
  val specialTrailingAccessory: ListItemAccessory? = null,
  var treatment: ListItemTreatment = ListItemTreatment.PRIMARY,
  val sideTextTint: ListItemSideTextTint = ListItemSideTextTint.PRIMARY,
  val enabled: Boolean = true,
  val selected: Boolean = false,
  val onClick: (() -> Unit)? = null,
  val pickerMenu: ListItemPickerMenu<*>? = null,
  val testTag: String? = null,
  val titleLabel: LabelModel? = null,
)

enum class ListItemTitleAlignment {
  LEFT,
  CENTER,
}

enum class ListItemAccessoryAlignment {
  TOP,
  CENTER,
}

enum class ListItemTreatment {
  PRIMARY,
  SECONDARY,
  TERTIARY,
  PRIMARY_TITLE,
  SECONDARY_DISPLAY,
}

enum class ListItemTitleBackgroundTreatment {
  RECOVERY,
}

enum class ListItemSideTextTint {
  PRIMARY,
  SECONDARY,
  GREEN,
}

fun ListItemAccessory.disable(): ListItemAccessory {
  return when (this) {
    is ButtonAccessory ->
      ButtonAccessory(
        model =
          ButtonModel(
            text = model.text,
            isEnabled = false,
            isLoading = model.isLoading,
            leadingIcon = model.leadingIcon,
            treatment = model.treatment,
            size = model.size,
            onClick = StandardClick {},
            testTag = model.testTag
          )
      )
    is IconAccessory ->
      IconAccessory(
        model =
          IconModel(
            iconImage = model.iconImage,
            iconSize = model.iconSize,
            iconBackgroundType = model.iconBackgroundType,
            iconTint = On30,
            text = model.text
          )
      )
    is SwitchAccessory ->
      SwitchAccessory(
        model =
          SwitchModel(
            checked = model.checked,
            onCheckedChange = model.onCheckedChange,
            enabled = false
          )
      )
    is TextAccessory -> this
    is CircularCharacterAccessory -> this
  }
}
