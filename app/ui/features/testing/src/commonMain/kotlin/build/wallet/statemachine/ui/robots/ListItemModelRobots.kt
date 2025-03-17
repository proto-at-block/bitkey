package build.wallet.statemachine.ui.robots

import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

fun ListItemModel.shouldHaveTrailingIconAccessoryButton(): IconAccessory {
  return trailingAccessory!!.shouldBeTypeOf<IconAccessory>()
}

fun ListItemModel.clickTrailingIconAccessoryButton() {
  shouldHaveTrailingIconAccessoryButton()
    .shouldBeClickable()
    .onClick!!()
}

fun IconAccessory.shouldBeClickable(): IconAccessory =
  shouldNotBeNull().apply {
    onClick.shouldNotBeNull()
  }
