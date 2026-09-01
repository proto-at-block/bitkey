package build.wallet.statemachine.recovery.hardware

import build.wallet.Progress
import build.wallet.statemachine.moneyhome.card.CardModel

fun HardwareRecoveryCardModel(
  title: String,
  subtitle: String? = null,
  delayPeriodProgress: Progress,
  delayPeriodRemainingSeconds: Long,
  onClick: () -> Unit,
) = CardModel.Status(
  id = "HardwareRecovery",
  title = title,
  subtitle = subtitle,
  leadingImage =
    CardModel.Status.Image.HardwareReplacementStatusProgress(
      progress = delayPeriodProgress,
      remainingSeconds = delayPeriodRemainingSeconds
    ),
  onClick = onClick
)
