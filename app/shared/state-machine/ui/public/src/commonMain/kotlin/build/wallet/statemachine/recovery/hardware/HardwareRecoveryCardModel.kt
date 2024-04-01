package build.wallet.statemachine.recovery.hardware

import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel

fun HardwareRecoveryCardModel(
  title: String,
  subtitle: String? = null,
  delayPeriodProgress: Float,
  delayPeriodRemainingSeconds: Long,
  onClick: () -> Unit,
) = CardModel(
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      string = title,
      substringToColor = emptyMap()
    ),
  subtitle = subtitle,
  leadingImage =
    CardModel.CardImage.DynamicImage.HardwareReplacementStatusProgress(
      progress = delayPeriodProgress,
      remainingSeconds = delayPeriodRemainingSeconds
    ),
  content = null,
  style = CardModel.CardStyle.Gradient(),
  onClick = onClick
)
