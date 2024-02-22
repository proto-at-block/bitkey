package build.wallet.statemachine.transactions

import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.platform.web.BrowserNavigator
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconArrowUpRight
import build.wallet.statemachine.core.Icon.SmallIconLightning
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

fun TransactionDetailModel(
  feeBumpEnabled: Boolean,
  recipientAddress: String,
  headerTitle: String,
  headerIcon: Icon,
  isLoading: Boolean,
  onLoaded: (BrowserNavigator) -> Unit,
  onViewTransaction: () -> Unit,
  onClose: () -> Unit,
  onSpeedUpTransaction: () -> Unit,
  content: ImmutableList<DataList>,
) = FormBodyModel(
  onLoaded = onLoaded,
  primaryButton =
    if (feeBumpEnabled) {
      ButtonModel(
        leadingIcon = SmallIconLightning,
        text = "Speed Up",
        treatment = ButtonModel.Treatment.Secondary,
        size = Footer,
        isLoading = isLoading,
        onClick =
          Click.standardClick {
            onSpeedUpTransaction()
          }
      )
    } else {
      ButtonModel(
        leadingIcon = SmallIconArrowUpRight,
        text = "View Transaction",
        size = Footer,
        onClick =
          Click.standardClick {
            onViewTransaction()
          }
      )
    },
  secondaryButton =
    if (feeBumpEnabled) {
      ButtonModel(
        leadingIcon = SmallIconArrowUpRight,
        text = "View Transaction",
        size = Footer,
        onClick =
          Click.standardClick {
            onViewTransaction()
          }
      )
    } else {
      null
    },
  onBack = onClose,
  onSwipeToDismiss = onClose,
  header =
    FormHeaderModel(
      icon = headerIcon,
      headline = headerTitle,
      subline = recipientAddress,
      sublineTreatment = MONO,
      alignment = CENTER
    ),
  toolbar =
    ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onClose)
    ),
  mainContentList = content,
  id = MoneyHomeEventTrackerScreenId.TRANSACTION_DETAIL
)
