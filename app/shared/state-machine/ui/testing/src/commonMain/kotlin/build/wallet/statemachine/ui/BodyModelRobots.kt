package build.wallet.statemachine.ui

import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

fun FormBodyModel.clickPrimaryButton() {
  primaryButton
    .shouldNotBeNull()
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

fun FormBodyModel.clickSecondaryButton() {
  secondaryButton
    .shouldNotBeNull()
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

fun FormBodyModel.shouldHaveTrailingAccessoryButton(): ButtonModel {
  requireNotNull(toolbar)
  requireNotNull(toolbar!!.trailingAccessory)
  return toolbar!!.trailingAccessory!!.shouldBeTypeOf<ButtonAccessory>().model
}

fun FormBodyModel.clickTrailingAccessoryButton() {
  shouldHaveTrailingAccessoryButton()
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

fun PairNewHardwareBodyModel.clickPrimaryButton() {
  primaryButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

fun FormBodyModel.getMainContentListItemAtIndex(index: Int): ListItemModel {
  val listGroup = mainContentList.firstNotNullOf { it as? FormMainContentModel.ListGroup }
  return listGroup.listGroupModel.items[index]
}

fun FormBodyModel.findMainContentListItem(predicate: (ListItemModel) -> Boolean): ListItemModel {
  val listGroup = mainContentList.firstNotNullOf { it as? FormMainContentModel.ListGroup }
  return listGroup.listGroupModel.items.first(predicate)
}

fun FormBodyModel.clickMainContentListItemAtIndex(index: Int) {
  getMainContentListItemAtIndex(index).onClick.shouldNotBeNull().invoke()
}

fun FormBodyModel.clickMainContentListItemTrailingButtonAtIndex(index: Int) {
  getMainContentListItemAtIndex(index)
    .trailingAccessory.shouldNotBeNull().shouldBeTypeOf<ListItemAccessory.ButtonAccessory>()
    .model.onClick()
}

fun FormBodyModel.inputTextToMainContentTextInputItem(text: String): FormBodyModel {
  val inputItem = mainContentList.firstNotNullOf { it as? FormMainContentModel.TextInput }
  inputItem.fieldModel.onValueChange(text, 0..0)
  return this
}

fun FormBodyModel.clickMainContentListFooterButton() {
  mainContentList.firstNotNullOf { it as? FormMainContentModel.ListGroup }
    .listGroupModel
    .footerButton
    .shouldNotBeNull()
    .onClick()
}

fun FormBodyModel.inputTextToMainContentVerificationCodeInputItem(text: String): FormBodyModel {
  val inputItem =
    mainContentList.firstNotNullOf { it as? FormMainContentModel.VerificationCodeInput }
  inputItem.fieldModel.onValueChange(text, 0..0)
  return this
}
