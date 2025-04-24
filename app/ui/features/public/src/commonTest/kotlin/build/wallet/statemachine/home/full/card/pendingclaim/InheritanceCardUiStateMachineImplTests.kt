package build.wallet.statemachine.home.full.card.pendingclaim

import build.wallet.Progress
import build.wallet.bitkey.inheritance.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.InheritanceCardServiceFake
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.*
import build.wallet.statemachine.moneyhome.card.inheritance.*
import build.wallet.statemachine.moneyhome.card.inheritance.BenefactorLockedCompleteClaimCardModel
import build.wallet.statemachine.moneyhome.card.inheritance.BenefactorPendingClaimCardModel
import build.wallet.statemachine.moneyhome.card.inheritance.BeneficiaryPendingClaimCardModel
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import build.wallet.time.someInstant
import build.wallet.ui.model.StandardClick
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class InheritanceCardUiStateMachineImplTests : FunSpec({

  val clock = ClockFake()
  val dateTimeFormatter = DateTimeFormatterMock()
  val timeZoneProvider = TimeZoneProviderMock()
  val inheritanceCardService = InheritanceCardServiceFake()
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )

  val stateMachine = InheritanceCardUiStateMachineImpl(
    inheritanceCardService = inheritanceCardService,
    inheritanceService = inheritanceService,
    clock = clock,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider
  )
  val props = InheritanceCardUiProps(
    completeClaim = {},
    denyClaim = {},
    moveFundsCallToAction = {}
  )

  beforeTest {
    clock.reset()
    inheritanceCardService.reset()
    inheritanceService.reset()
  }

  test("displays single beneficiary pending claim card") {
    inheritanceCardService.cardsToDisplay.value = listOf(BeneficiaryPendingClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BeneficiaryPendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDateWithYear(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}.",
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

  test("displays multiple beneficiary pending claim cards") {
    inheritanceCardService.cardsToDisplay.value = listOf(
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
            BeneficiaryPendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDateWithYear(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}.",
              isPendingClaim = true,
              timeRemaining = 180.days,
              progress = Progress.Zero,
              onClick = null
            ),
            BeneficiaryPendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDateWithYear(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}.",
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

  test("displays locked beneficiary claim card") {
    inheritanceCardService.cardsToDisplay.value = listOf(BeneficiaryLockedClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().shouldBe(
        equalIgnoringOnClick(
          listOf(
            BeneficiaryPendingClaimCardModel(
              title = "Claim approved",
              subtitle = "Transfer funds now.",
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

  test("displays multiple beneficiary locked claim cards") {
    inheritanceCardService.cardsToDisplay.value = listOf(
      BeneficiaryLockedClaimFake,
      BeneficiaryLockedClaimFake.copy(
        claimId = InheritanceClaimId("claim-benefactor-locked-id2")
      )
    )

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BeneficiaryPendingClaimCardModel(
              title = "Claim approved",
              subtitle = "Transfer funds now.",
              isPendingClaim = false,
              timeRemaining = Duration.ZERO,
              progress = Progress.Full,
              onClick = null
            ),
            BeneficiaryPendingClaimCardModel(
              title = "Claim approved",
              subtitle = "Transfer funds now.",
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

  test("displays both beneficiary locked and beneficiary pending claim cards") {
    inheritanceCardService.cardsToDisplay.value = listOf(BeneficiaryPendingClaimFake, BeneficiaryLockedClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BeneficiaryPendingClaimCardModel(
              title = "Inheritance claim pending",
              subtitle = "Funds available ${dateTimeFormatter.shortDate(
                someInstant.toLocalDateTime(
                  TimeZone.currentSystemDefault()
                )
              )}.",
              isPendingClaim = true,
              timeRemaining = 180.days,
              progress = Progress.Zero,
              onClick = null
            ),
            BeneficiaryPendingClaimCardModel(
              title = "Claim approved",
              subtitle = "Transfer funds now.",
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

  test("displays benefactor pending claim warning") {
    inheritanceCardService.cardsToDisplay.value = listOf(BenefactorPendingClaimFake)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BenefactorPendingClaimCardModel(
              title = "Inheritance claim initiated",
              subtitle = "Decline claim by date-time to retain control of your funds.",
              onClick = null
            )
          )
        )
      )
    }
  }

  test("after delay period beneficiary sees complete card") {
    inheritanceCardService.cardsToDisplay.value = listOf(BeneficiaryPendingClaimFake.copy(delayEndTime = someInstant.minus(1.days)))

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BeneficiaryPendingClaimCardModel(
              title = "Claim approved",
              subtitle = "Transfer funds now.",
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

  test("after delay period benefactor still sees pending card") {
    inheritanceCardService.cardsToDisplay.value = listOf(BenefactorPendingClaimFake.copy(delayEndTime = someInstant.minus(1.days)))

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BenefactorPendingClaimCardModel(
              title = "Inheritance claim initiated",
              subtitle = "Decline claim by date-time to retain control of your funds.",
              onClick = null
            )
          )
        )
      )
    }
  }

  test("after complete, benefactor sees updated transfer funds card") {
    inheritanceCardService.cardsToDisplay.value = listOf(BenefactorCompleteClaim)

    stateMachine.test(props) {
      awaitItem().shouldBe(emptyList())
      awaitItem().should(
        equalIgnoringOnClick(
          listOf(
            BenefactorLockedCompleteClaimCardModel(
              title = "Inheritance approved",
              subtitle = "To retain control of any remaining funds, transfer them to a new wallet.",
              onClick = StandardClick {}
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
        val content = (it.content as? CardContent.PendingClaim)?.copy(onClick = null)
        val model = (it.style as? CardStyle.Callout)?.model?.copy(onClick = null)
        if (model != null) {
          it.copy(style = it.style.copy(model = model), onClick = null, content = content)
        } else {
          it.copy(onClick = null, content = content)
        }
      }
      val actualCopy = value.map {
        val content = (it.content as? CardContent.PendingClaim)?.copy(onClick = null)
        val model = (it.style as? CardStyle.Callout)?.model?.copy(onClick = null)
        if (model != null) {
          it.copy(style = it.style.copy(model = model), onClick = null, content = content)
        } else {
          it.copy(onClick = null, content = content)
        }
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
