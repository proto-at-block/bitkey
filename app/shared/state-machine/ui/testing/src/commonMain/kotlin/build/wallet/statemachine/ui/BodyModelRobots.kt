package build.wallet.statemachine.ui

import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

suspend fun FormBodyModel.clickPrimaryButton() {
  requireNotNull(primaryButton)
  primaryButton!!.onClick()
}

suspend fun FormBodyModel.clickSecondaryButton() {
  requireNotNull(secondaryButton)
  secondaryButton!!.onClick()
}

suspend fun FormBodyModel.clickTrailingAccessoryButton() {
  requireNotNull(toolbar)
  requireNotNull(toolbar!!.trailingAccessory)
  val accessory = toolbar!!.trailingAccessory!!.shouldBeTypeOf<ButtonAccessory>()
  accessory.model.onClick()
}

suspend fun PairNewHardwareBodyModel.clickPrimaryButton() {
  primaryButton.onClick()
}

suspend fun FormBodyModel.getMainContentListItemAtIndex(index: Int): ListItemModel {
  val listGroup = mainContentList.firstNotNullOf { it as? FormMainContentModel.ListGroup }
  return listGroup.listGroupModel.items[index]
}

suspend fun FormBodyModel.findMainContentListItem(
  predicate: (ListItemModel) -> Boolean,
): ListItemModel {
  val listGroup = mainContentList.firstNotNullOf { it as? FormMainContentModel.ListGroup }
  return listGroup.listGroupModel.items.first(predicate)
}

suspend fun FormBodyModel.clickMainContentListItemAtIndex(index: Int) {
  getMainContentListItemAtIndex(index).onClick.shouldNotBeNull().invoke()
}

suspend fun FormBodyModel.clickMainContentListItemTrailingButtonAtIndex(index: Int) {
  getMainContentListItemAtIndex(index)
    .trailingAccessory.shouldNotBeNull().shouldBeTypeOf<ListItemAccessory.ButtonAccessory>()
    .model.onClick()
}

suspend fun FormBodyModel.inputTextToMainContentTextInputItem(text: String): FormBodyModel {
  val inputItem = mainContentList.firstNotNullOf { it as? FormMainContentModel.TextInput }
  inputItem.fieldModel.onValueChange(text, 0..0)
  return this
}

suspend fun FormBodyModel.clickMainContentListFooterButton() {
  val footerButton =
    mainContentList.firstNotNullOf { it as? FormMainContentModel.ListGroup }
      .listGroupModel
      .footerButton
      .shouldNotBeNull()
      .onClick()
}

suspend fun FormBodyModel.inputTextToMainContentVerificationCodeInputItem(
  text: String,
): FormBodyModel {
  val inputItem = mainContentList.firstNotNullOf { it as? FormMainContentModel.VerificationCodeInput }
  inputItem.fieldModel.onValueChange(text, 0..0)
  return this
}

fun SuccessBodyModel.clickPrimaryButton() {
  (style as SuccessBodyModel.Style.Explicit).primaryButton.onClick()
}
