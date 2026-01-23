package build.wallet.statemachine.transactions

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

internal data class BitcoinTransactionSpeedUpEducationBodyModel(
  val onSpeedUpTransaction: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = SendEventTrackerScreenId.SEND_SPEED_UP_EDUCATION_SHEET,
    onBack = onClose,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Speed up transactions",
      subline = """
            If your Bitcoin transaction is taking longer than expected, you can try speeding it up.
            
            A common problem that can occur is when someone sends a payment with a fee that isn't high enough to get confirmed, causing it to get stuck in the mempool.
            
            We’ll take the guess work out by providing a fee that should get your transfer confirmed quickly.
      """.trimIndent(),
      iconModel = IconModel(
        icon = Icon.SmallIconSpeed,
        iconSize = IconSize.Small,
        iconTint = IconTint.Primary,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.Large,
          color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
        )
      ),
      sublineTreatment = FormHeaderModel.SublineTreatment.REGULAR
    ),
    primaryButton = ButtonModel(
      text = "Try speeding up",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onSpeedUpTransaction)
    ),
    renderContext = RenderContext.Sheet
  )
