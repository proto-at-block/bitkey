package build.wallet.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.*
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun DataRowGroupSingleItemPreview() {
  PreviewWalletTheme {
    DataGroup(
      rows =
        DataList(
          items =
            immutableListOf(
              Data(
                title = "Miner fee",
                sideText = "bc1q...xyB1",
                sideTextTreatment = Data.SideTextTreatment.PRIMARY
              )
            ),
          total = null
        )
    )
  }
}

@Preview
@Composable
private fun DataRowGroupTwoItemsPreview() {
  PreviewWalletTheme {
    DataGroup(
      rows =
        DataList(
          items =
            immutableListOf(
              Data(
                title = "Miner fee",
                sideText = "bc1q...xyB1",
                sideTextTreatment = Data.SideTextTreatment.PRIMARY
              ),
              Data(
                title = "Miner fee",
                sideText = "bc1q...xyB1",
                sideTextTreatment = Data.SideTextTreatment.PRIMARY
              )
            ),
          total = null
        )
    )
  }
}

@Preview
@Composable
fun DataRowGroupWithTotalPreview() {
  PreviewWalletTheme {
    DataRowGroupWithTotal()
  }
}

@Preview
@Composable
fun DataGroupWithLateTransactionPreview() {
  PreviewWalletTheme {
    DataGroupWithLateTransaction()
  }
}

@Composable
fun DataRowGroupWithTotal() =
  DataGroup(
    rows =
      DataList(
        items =
          immutableListOf(
            Data(
              title = "Miner fee",
              sideText = "bc1q...xyB1",
              sideTextTreatment = Data.SideTextTreatment.PRIMARY
            ),
            Data(
              title = "Miner fee",
              sideText = "bc1q...xyB1",
              sideTextTreatment = Data.SideTextTreatment.PRIMARY
            )
          ),
        total =
          Data(
            title = "Total",
            sideText = "$21.36",
            secondarySideText = "0.0010 BTC",
            sideTextType = Data.SideTextType.BODY2BOLD,
            sideTextTreatment = Data.SideTextTreatment.PRIMARY
          )
      )
  )

@Composable
fun DataGroupWithLateTransaction() =
  DataGroup(
    rows =
      DataList(
        items =
          immutableListOf(
            Data(
              title = "Should have arrived by",
              sideText = "Aug 7, 12:14pm",
              sideTextTreatment = Data.SideTextTreatment.STRIKETHROUGH,
              sideTextType = Data.SideTextType.REGULAR,
              secondarySideText = "7m late",
              secondarySideTextType = Data.SideTextType.BOLD,
              secondarySideTextTreatment = Data.SideTextTreatment.WARNING,
              explainer =
                Data.Explainer(
                  title = "Speed up transaction?",
                  subtitle = "You can speed up this transaction by increasing the network fee.",
                  iconButton = IconButtonModel(
                    iconModel = IconModel(
                      icon = Icon.SmallIconInformationFilled,
                      iconSize = IconSize.XSmall,
                      iconBackgroundType = IconBackgroundType.Circle(
                        circleSize = IconSize.XSmall
                      ),
                      iconTint = IconTint.Foreground,
                      iconOpacity = 0.20f
                    ),
                    onClick = StandardClick { }
                  )
                )
            )
          )
      )
  )
