package build.wallet.ui.app.transactions

import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.transactions.FailedPartnerTransactionBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import io.kotest.core.spec.style.FunSpec

class FailedPartnerTransactionSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Failed Partner Transaction Screen") {
    paparazzi.snapshot {
      FormScreen(
        model = FailedPartnerTransactionBodyModel(
          headerIcon = IconModel(
            iconImage = IconImage.LocalImage(Icon.Bitcoin),
            iconSize = IconSize.Avatar
          ),
          headline = "There was an issue with your Partner transaction",
          subline = "Visit Partner for more information.",
          content = immutableListOf(
            FormMainContentModel.Divider,
            DataList(
              items = immutableListOf(
                Data(
                  title = "Amount",
                  sideText = "$1.00",
                  secondarySideText = "1000 sats"
                )
              )
            )
          ),
          buttonModel = ButtonModel(
            text = "Go to Partner",
            treatment = ButtonModel.Treatment.Primary,
            leadingIcon = Icon.SmallIconArrowUpRight,
            size = ButtonModel.Size.Footer,
            testTag = null,
            onClick = StandardClick {}
          ),
          onClose = {}
        )
      )
    }
  }
})
