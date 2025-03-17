package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.callout.CalloutModel.Treatment
import build.wallet.ui.theme.WalletTheme

@Preview
@Composable
fun CalloutCardPreviews() {
  Box(
    modifier =
      Modifier
        .background(color = WalletTheme.colors.background)
        .padding(24.dp)
  ) {
    MoneyHomeCard(
      model =
        CardModel(
          title = null,
          content = null,
          style = CardModel.CardStyle.Callout(
            CalloutModel(
              title = "Inheritance claim initiated",
              subtitle = LabelModel.StringModel("Decline claim by 10/21/2024 to retain control of your funds"),
              treatment = Treatment.Danger,
              leadingIcon = Icon.SmallIconInformationFilled,
              trailingIcon = Icon.SmallIconArrowRight,
              onClick = null
            )
          )
        )
    )
  }
}
