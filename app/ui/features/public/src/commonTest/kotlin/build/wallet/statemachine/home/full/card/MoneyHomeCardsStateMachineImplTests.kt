package build.wallet.statemachine.home.full.card

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsProps
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiProps
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiProps
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiProps
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class MoneyHomeCardsStateMachineImplTests : FunSpec({
  val startSweepCardUiStateMachine =
    object : StartSweepCardUiStateMachine, StateMachineMock<StartSweepCardUiProps, CardModel?>(
      initialModel = null
    ) {}

  val bitcoinPriceCardUiStateMachine =
    object : BitcoinPriceCardUiStateMachine, StateMachineMock<BitcoinPriceCardUiProps, CardModel?>(
      initialModel = null
    ) {}

  val inheritanceCardUiStateMachine =
    object : InheritanceCardUiStateMachine,
      StateMachineMock<InheritanceCardUiProps, List<CardModel>>(
        initialModel = emptyList()
      ) {}

  val stateMachine =
    MoneyHomeCardsUiStateMachineImpl(
      startSweepCardUiStateMachine = startSweepCardUiStateMachine,
      bitcoinPriceCardUiStateMachine = bitcoinPriceCardUiStateMachine,
      inheritanceCardUiStateMachine = inheritanceCardUiStateMachine
    )

  val props =
    MoneyHomeCardsProps(
      startSweepCardUiProps = StartSweepCardUiProps(
        onStartSweepClicked = {}
      ),
      bitcoinPriceCardUiProps = BitcoinPriceCardUiProps(
        accountId = FullAccountIdMock,
        f8eEnvironment = FullAccountMock.config.f8eEnvironment,
        appFunctionalityStatus = AppFunctionalityStatus.FullFunctionality,
        onOpenPriceChart = {}
      ),
      inheritanceCardUiProps = InheritanceCardUiProps(
        completeClaim = {},
        denyClaim = {},
        moveFundsCallToAction = {}
      )
    )

  afterTest {
    startSweepCardUiStateMachine.reset()
    bitcoinPriceCardUiStateMachine.reset()
    inheritanceCardUiStateMachine.reset()
  }

  test("should return empty card list when all child state machines return null or empty") {
    stateMachine.test(props) {
      awaitItem().cards.shouldBeEmpty()
    }
  }

  test("should include start sweep card when available") {
    startSweepCardUiStateMachine.emitModel(createTestCard("Start Sweep"))

    stateMachine.test(props) {
      awaitItem().cards.shouldBeSingleton()
    }
  }

  test("should include bitcoin price card when available") {
    bitcoinPriceCardUiStateMachine.emitModel(createTestCard("Bitcoin Price"))

    stateMachine.test(props) {
      awaitItem().cards.shouldBeSingleton()
    }
  }

  test("should include inheritance cards when available") {
    val inheritanceCards = listOf(
      createTestCard("Inheritance Card 1"),
      createTestCard("Inheritance Card 2")
    )
    inheritanceCardUiStateMachine.emitModel(inheritanceCards)

    stateMachine.test(props) {
      awaitItem().cards.shouldHaveSize(2)
    }
  }

  test("should maintain correct card order: inheritance, sweep, bitcoin price") {
    val sweepCard = createTestCard("Start Sweep")
    val bitcoinPriceCard = createTestCard("Bitcoin Price")
    val inheritanceCard = createTestCard("Inheritance")

    startSweepCardUiStateMachine.emitModel(sweepCard)
    bitcoinPriceCardUiStateMachine.emitModel(bitcoinPriceCard)
    inheritanceCardUiStateMachine.emitModel(listOf(inheritanceCard))

    stateMachine.test(props) {
      val cards = awaitItem().cards
      cards[0] shouldBe inheritanceCard
      cards[1] shouldBe sweepCard
      cards[2] shouldBe bitcoinPriceCard
    }
  }

  test("should react to state machine changes") {
    // Initially empty
    stateMachine.test(props) {
      awaitItem().cards.shouldBeEmpty()

      // Add a card
      startSweepCardUiStateMachine.emitModel(createTestCard("Start Sweep"))
      awaitItem().cards.shouldBeSingleton()

      // Remove the card
      startSweepCardUiStateMachine.emitModel(null)
      awaitItem().cards.shouldBeEmpty()
    }
  }
})

private fun createTestCard(title: String) =
  CardModel.Status(
    id = title,
    title = title,
    subtitle = null,
    leadingImage = CardModel.Status.Image.StaticImage(Icon.Bitkey)
  )
