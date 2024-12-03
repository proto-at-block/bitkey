package build.wallet.statemachine.home.full.card.pendingclaim

import build.wallet.Progress
import build.wallet.bitkey.inheritance.*
import build.wallet.inheritance.InheritanceCardServiceFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.*
import build.wallet.statemachine.moneyhome.card.pendingclaim.PendingClaimCardModel
import build.wallet.statemachine.moneyhome.card.pendingclaim.PendingClaimCardUiProps
import build.wallet.statemachine.moneyhome.card.pendingclaim.PendingClaimCardUiStateMachineImpl
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class PendingClaimCardUiStateMachineImplTests : FunSpec({

  val clock = ClockFake()
  val dateTimeFormatter = DateTimeFormatterMock()
  val timeZoneProvider = TimeZoneProviderMock()
  val inheritanceCardService = InheritanceCardServiceFake()

  val stateMachine = PendingClaimCardUiStateMachineImpl(
    inheritanceCardService = inheritanceCardService,
    clock = clock,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider
  )
  val props = PendingClaimCardUiProps(null)

  beforeTest {
    clock.reset()
    inheritanceCardService.reset()
  }

  test("displays single pending claim card") {
    inheritanceCardService.claimCardsToDisplay.value = listOf(BeneficiaryPendingClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            PendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDate(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}",
              isPendingClaim = true,
              timeRemaining = 180.days,
              progress = Progress.Zero,
              onClick = null
            )
          )
        )
      )
    }
  }

  test("displays multiple pending claim cards") {
    inheritanceCardService.claimCardsToDisplay.value = listOf(
      BeneficiaryPendingClaimFake,
      BeneficiaryPendingClaimFake.copy(
        claimId = InheritanceClaimId("claim-benefactor-pending-id2"),
        delayEndTime = someInstant.plus(360.days)
      )
    )

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            PendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDate(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}",
              isPendingClaim = true,
              timeRemaining = 180.days,
              progress = Progress.Zero,
              onClick = null
            ),
            PendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDate(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}",
              isPendingClaim = true,
              timeRemaining = 360.days,
              progress = Progress.Zero,
              onClick = null
            )
          )
        )
      )
    }
  }

  test("displays locked claim card") {
    inheritanceCardService.claimCardsToDisplay.value = listOf(BeneficiaryLockedClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().shouldBe(
        listOf(
          PendingClaimCardModel(
            title = "Claim complete",
            subtitle = "Transfer funds now",
            isPendingClaim = false,
            timeRemaining = Duration.ZERO,
            progress = Progress.Full,
            onClick = props.onClick
          )
        )
      )
    }
  }

  test("displays multiple locked claim cards") {
    inheritanceCardService.claimCardsToDisplay.value = listOf(
      BeneficiaryLockedClaimFake,
      BeneficiaryLockedClaimFake.copy(
        claimId = InheritanceClaimId("claim-benefactor-locked-id2")
      )
    )

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().shouldBe(
        listOf(
          PendingClaimCardModel(
            title = "Claim complete",
            subtitle = "Transfer funds now",
            isPendingClaim = false,
            timeRemaining = Duration.ZERO,
            progress = Progress.Full,
            onClick = props.onClick
          ),
          PendingClaimCardModel(
            title = "Claim complete",
            subtitle = "Transfer funds now",
            isPendingClaim = false,
            timeRemaining = Duration.ZERO,
            progress = Progress.Full,
            onClick = props.onClick
          )
        )
      )
    }
  }

  test("displays both locked and pending claim cards") {
    inheritanceCardService.claimCardsToDisplay.value = listOf(BeneficiaryPendingClaimFake, BeneficiaryLockedClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            PendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDate(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}",
              isPendingClaim = true,
              timeRemaining = 180.days,
              progress = Progress.Zero,
              onClick = null
            ),
            PendingClaimCardModel(
              title = "Claim complete",
              subtitle = "Transfer funds now",
              isPendingClaim = false,
              timeRemaining = Duration.ZERO,
              progress = Progress.Full,
              onClick = null
            )
          )
        )
      )
    }
  }
})

fun equalIgnoringOnClick(expected: List<CardModel>) =
  object : Matcher<List<CardModel>> {
    override fun test(value: List<CardModel>): MatcherResult {
      val expectedCopy = expected.map {
        (it.content as CardContent.PendingClaim).copy(onClick = null)
      }
      val actualCopy = value.map {
        (it.content as CardContent.PendingClaim).copy(onClick = null)
      }
      val passed = expectedCopy == actualCopy
      return MatcherResult(
        passed,
        {
          "CardModel instances are not equal when ignoring onClick.\n" +
            "Expected: $expectedCopy\n" +
            "Actual  : $actualCopy"
        },
        {
          "CardModel instances should not be equal when ignoring onClick, but they were equal."
        }
      )
    }
  }
