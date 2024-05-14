@file:JvmName("DataRowGroupKt")

package build.wallet.ui.data

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun DataGroup(
  modifier: Modifier = Modifier,
  rows: DataList,
) {
  val lineColor = Color.Black.copy(alpha = 0.05F)
  Column(
    modifier =
      modifier
        .border(
          width = 2.dp,
          color = lineColor,
          shape = RoundedCornerShape(16.dp)
        )
        .clip(RoundedCornerShape(16.dp)),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    rows.hero?.let {
      Spacer(Modifier.height(20.dp))
      DataHero(model = it)
    }
    rows.items.forEachIndexed { idx, element ->
      DataRowRegular(model = element, isFirst = idx == 0)
    }
    rows.total?.let {
      Divider(color = lineColor)
      DataRowTotal(
        modifier =
          Modifier
            .padding(horizontal = 16.dp),
        model = it
      )
    }
    rows.buttons.forEach {
      Button(modifier = Modifier.padding(vertical = 8.dp), model = it)
      Spacer(Modifier.height(20.dp))
    }
  }
}

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
              title = "Total Cost",
              sideText = "$21.36",
              secondarySideText = "(0.0010 BTC)",
              sideTextType = Data.SideTextType.BODY2BOLD,
              sideTextTreatment = Data.SideTextTreatment.PRIMARY
            )
        )
    )
  }
}

@Preview
@Composable
fun DataGroupWithLateTransactionPreview() {
  PreviewWalletTheme {
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
                    title = "Taking longer than usual",
                    subtitle = "You can either wait for this transaction to be confirmed or speed it up – you'll need to pay a higher network fee.",
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
  }
}
