package build.wallet.statemachine.moneyhome.card.inheritance

import build.wallet.Progress
import build.wallet.statemachine.moneyhome.card.CardModel
import kotlin.time.Duration

internal fun BeneficiaryPendingClaimCardModel(
  title: String,
  subtitle: String,
  isPendingClaim: Boolean,
  timeRemaining: Duration,
  progress: Progress,
  onClick: (() -> Unit)? = null,
) = CardModel(
  title = null,
  content = CardModel.CardContent.PendingClaim(
    title = title,
    subtitle = subtitle,
    isPendingClaim = isPendingClaim,
    timeRemaining = timeRemaining,
    progress = progress,
    onClick = onClick
  ),
  style = CardModel.CardStyle.Plain
)
