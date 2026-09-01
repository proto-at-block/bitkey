package build.wallet.statemachine.moneyhome.card.inheritance

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.callout.CalloutModel.Treatment

internal fun BenefactorPendingClaimCardModel(
  id: String,
  title: String,
  subtitle: String,
  onClick: Click? = null,
) = CardModel.Callout(
  id = "BenefactorPendingClaim:$id",
  callout = CalloutModel(
    title = title,
    subtitle = LabelModel.StringModel(subtitle),
    treatment = Treatment.Danger,
    useMonochromeStyle = true,
    leadingIconOverride = Icon.ShieldPerson,
    leadingIcon = Icon.Information,
    trailingIcon = Icon.ArrowRight,
    onClick = onClick
  )
)
