package build.wallet.statemachine.moneyhome.card.inheritance

import build.wallet.Progress
import build.wallet.statemachine.moneyhome.card.CardModel
import kotlin.time.Duration

internal fun BeneficiaryPendingClaimCardModel(
  id: String,
  title: String,
  subtitle: String,
  state: CardModel.PendingClaim.State,
  timeRemaining: Duration,
  progress: Progress,
  onClick: (() -> Unit)? = null,
) = CardModel.PendingClaim(
  id = "BeneficiaryPendingClaim:$id",
  title = title,
  subtitle = subtitle,
  state = state,
  timeRemaining = timeRemaining,
  progress = progress,
  onClick = onClick,
)
