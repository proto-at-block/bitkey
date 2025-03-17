package build.wallet.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data.SideTextTreatment
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.*
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun DataRowPreview() {
  PreviewWalletTheme {
    DataRowRegular(
      isFirst = false,
      model =
        Data(
          title = "Miner Fee",
          sideText = "bc1q...xyB1",
          sideTextTreatment = SideTextTreatment.PRIMARY
        )
    )
  }
}

@Preview
@Composable
private fun DataRowWithSecondaryPreview() {
  PreviewWalletTheme {
    DataRowRegular(
      isFirst = false,
      model =
        Data(
          title = "Miner Fee",
          sideText = "bc1q...xyB1",
          secondarySideText = "bc1q...xyB1",
          sideTextTreatment = SideTextTreatment.PRIMARY
        )
    )
  }
}

@Preview
@Composable
private fun DataRowWithExplainerAndIconButtonPreview() {
  PreviewWalletTheme {
    DataRowRegular(
      isFirst = false,
      model = Data(
        title = "Transaction Details",
        sideText = "bc1q...xyB1",
        explainer = Data.Explainer(
          title = "Speed up transaction?",
          subtitle = "You can speed up this transaction by increasing the network fee.",
          iconButton = IconButtonModel(
            iconModel = IconModel(
              icon = build.wallet.statemachine.core.Icon.SmallIconInformationFilled,
              iconSize = IconSize.XSmall,
              iconBackgroundType = IconBackgroundType.Circle(
                circleSize = IconSize.XSmall
              ),
              iconTint = IconTint.Foreground,
              iconOpacity = 0.20f
            ),
            onClick = StandardClick { }
          )
        ),
        sideTextTreatment = SideTextTreatment.PRIMARY
      )
    )
  }
}
