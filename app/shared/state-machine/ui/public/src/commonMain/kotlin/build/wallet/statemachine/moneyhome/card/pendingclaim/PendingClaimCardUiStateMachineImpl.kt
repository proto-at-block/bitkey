package build.wallet.statemachine.moneyhome.card.pendingclaim

import androidx.compose.runtime.*
import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.inheritance.InheritanceCardService
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.*
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

class PendingClaimCardUiStateMachineImpl(
  private val inheritanceCardService: InheritanceCardService,
  private val clock: Clock,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
) : PendingClaimCardUiStateMachine {
  lateinit var store: SuspendSettings

  @Composable
  override fun model(props: PendingClaimCardUiProps): List<CardModel> {
    val scope = rememberStableCoroutineScope()

    val beneficiaryClaims by remember {
      inheritanceCardService.claimCardsToDisplay
    }.collectAsState(emptyList())

    return beneficiaryClaims
      .mapNotNull { claim ->
        when (claim) {
          is BeneficiaryClaim.LockedClaim ->
            PendingClaimCardModel(
              title = "Claim complete",
              subtitle = "Transfer funds now",
              isPendingClaim = false,
              timeRemaining = Duration.ZERO,
              progress = Progress.Full,
              onClick = props.onClick
            )
          is BeneficiaryClaim.PendingClaim ->
            PendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = subtitle(claim, dateTimeFormatter),
              isPendingClaim = true,
              timeRemaining = timeRemaining(claim),
              progress = progress(claim),
              onClick = {
                scope.launch {
                  inheritanceCardService.dismissPendingClaimCard(claim.claimId.value)
                }
              }
            )
          else -> null
        }
      }
  }

  private fun subtitle(
    pendingClaim: BeneficiaryClaim.PendingClaim,
    dateTimeFormatter: DateTimeFormatter,
  ): String {
    val formattedDate = dateTimeFormatter.shortDate(
      pendingClaim.delayEndTime.toLocalDateTime(
        timeZoneProvider.current()
      )
    )
    return "Funds available $formattedDate"
  }

  private fun timeRemaining(pendingClaim: BeneficiaryClaim.PendingClaim): Duration {
    return maxOf(pendingClaim.delayEndTime - clock.now(), Duration.ZERO)
  }

  private fun progress(pendingClaim: BeneficiaryClaim.PendingClaim): Progress {
    // Calculate the total duration between start and end time
    val totalDuration = pendingClaim.delayEndTime - pendingClaim.delayStartTime
    // Calculate the elapsed time from start time to current time
    val elapsedDuration = clock.now() - pendingClaim.delayStartTime
    // If we can't clamp here it's because elapsed duration has exceeded total duration and we haven't cleared the
    // pending claim from the store, so we default to 'progress full'.
    return (elapsedDuration / totalDuration).toFloat().asProgress().getOrElse { Progress.Full }
  }
}
