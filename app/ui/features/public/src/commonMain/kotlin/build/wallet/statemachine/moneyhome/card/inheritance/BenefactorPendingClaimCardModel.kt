package build.wallet.statemachine.moneyhome.card.inheritance

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.callout.CalloutModel.Treatment

internal fun BenefactorPendingClaimCardModel(
  title: String,
  subtitle: String,
  onClick: Click? = null,
) = CardModel(
  title = null,
  content = null,
  style = CardModel.CardStyle.Callout(
    CalloutModel(
      title = title,
      subtitle = LabelModel.StringModel(subtitle),
      treatment = Treatment.Danger,
      leadingIcon = Icon.SmallIconInformationFilled,
      trailingIcon = Icon.SmallIconArrowRight,
      onClick = onClick
    )
  )
)
