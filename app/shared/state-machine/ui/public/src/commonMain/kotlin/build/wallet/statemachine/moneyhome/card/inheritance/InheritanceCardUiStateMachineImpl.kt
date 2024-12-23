package build.wallet.statemachine.moneyhome.card.inheritance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceCardService
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.ui.model.StandardClick
import com.github.michaelbull.result.getOrElse
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class InheritanceCardUiStateMachineImpl(
  private val inheritanceCardService: InheritanceCardService,
  private val clock: Clock,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
) : InheritanceCardUiStateMachine {
  lateinit var store: SuspendSettings

  @Composable
  override fun model(props: InheritanceCardUiProps): List<CardModel> {
    val scope = rememberStableCoroutineScope()

    val inheritanceCards by remember {
      inheritanceCardService.cardsToDisplay
    }.collectAsState(emptyList())

    return inheritanceCards
      .mapNotNull { claim ->
        when (claim) {
          is BenefactorClaim.PendingClaim ->
            BenefactorPendingClaimCardModel(
              title = "Inheritance claim initiated",
              subtitle = benefactorPendingClaimSubtitle(claim),
              onClick = StandardClick { props.onClick?.invoke(claim) }
            )
          is BenefactorClaim.CompleteClaim, is BenefactorClaim.LockedClaim ->
            BenefactorLockedCompleteClaimCardModel(
              title = "Inheritance approved",
              subtitle = "To retain control of your funds, transfer them to a new wallet."
            )
          is BeneficiaryClaim.PendingClaim ->
            BeneficiaryPendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = beneficiaryPendingClaimSubtitle(claim),
              isPendingClaim = true,
              timeRemaining = timeRemaining(claim),
              progress = progress(claim),
              onClick = {
                scope.launch {
                  inheritanceCardService.dismissPendingBeneficiaryClaimCard(claim.claimId.value)
                }
              }
            )
          is BeneficiaryClaim.LockedClaim ->
            BeneficiaryPendingClaimCardModel(
              title = "Claim complete",
              subtitle = "Transfer funds now",
              isPendingClaim = false,
              timeRemaining = Duration.ZERO,
              progress = Progress.Full,
              onClick = { props.onClick?.invoke(claim) }
            )

          else -> null
        }
      }
  }

  private fun benefactorPendingClaimSubtitle(pendingClaim: BenefactorClaim.PendingClaim): String {
    val formattedDate = dateTimeFormatter.shortDate(
      pendingClaim.delayEndTime.toLocalDateTime(
        timeZoneProvider.current()
      )
    )
    return "Decline claim by $formattedDate to retain control of your funds."
  }

  private fun beneficiaryPendingClaimSubtitle(
    pendingClaim: BeneficiaryClaim.PendingClaim,
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
