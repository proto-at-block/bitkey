package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.activity.TransactionsActivityState
import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.bitcoin.transactions.TransactionsActivityServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.All
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.Some
import build.wallet.ui.model.list.ListItemModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import kotlinx.collections.immutable.toImmutableList

class TransactionsActivityUiStateMachineImplTests : FunSpec({

  val transactionsActivityService = TransactionsActivityServiceFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val stateMachine = TransactionsActivityUiStateMachineImpl(
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    bitcoinTransactionItemUiStateMachine = object : BitcoinTransactionItemUiStateMachine,
      StateMachineMock<BitcoinTransactionItemUiProps, ListItemModel>(
        initialModel = ListItemModel(
          title = "transactionId",
          secondaryText = "date",
          sideText = "amount",
          secondarySideText = "amountEquivalent",
          onClick = {}
        )
      ) {},
    transactionsActivityService = transactionsActivityService,
    partnerTransactionItemUiStateMachine = object : PartnerTransactionItemUiStateMachine,
      StateMachineMock<PartnerTransactionItemUiProps, ListItemModel>(
        initialModel = ListItemModel(
          title = "transactionId",
          secondaryText = "date",
          sideText = "amount",
          secondarySideText = "amountEquivalent",
          onClick = {}
        )
      ) {}
  )

  val transactionClickedCalls = turbines.create<Transaction>("onTransactionClicked")

  fun createProps(transactionVisibility: TransactionVisibility): TransactionsActivityProps {
    return TransactionsActivityProps(
      transactionVisibility = transactionVisibility,
      onTransactionClicked = transactionClickedCalls::add
    )
  }

  // List of 10 transactions, all pending
  val txnListAllPending = List(10) { BitcoinWalletTransaction(BitcoinTransactionFake) }
    .toImmutableList()

  // List of 10 transactions, all confirmed
  val txnListAllConfirmed = List(10) {
    BitcoinWalletTransaction(
      BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake))
    )
  }.toImmutableList()

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
    transactionsActivityService.reset()
  }

  test("model with All visibility") {
    transactionsActivityService.transactionsState.value =
      TransactionsActivityState.Loaded(txnListAllPending + txnListAllConfirmed)

    stateMachine.test(createProps(transactionVisibility = All)) {
      awaitItem().shouldNotBeNull().should {
        it.listModel.header.shouldBeNull()
        it.listModel.items.shouldHaveSize(20)
        it.hasMoreTransactions.shouldBeFalse()
      }
    }
  }

  test("model with Some visibility") {
    val numberOfVisibleTransactions = 3
    val transactionVisibility = Some(numberOfVisibleTransactions)

    // Test list of 1 transaction
    transactionsActivityService.transactionsState.value =
      TransactionsActivityState.Loaded(listOf(BitcoinWalletTransaction(BitcoinTransactionFake)))

    stateMachine.test(createProps(transactionVisibility)) {
      awaitItem().shouldNotBeNull().should {
        it.listModel.header.shouldBeNull()
        it.listModel.items.shouldHaveSize(1)
        it.hasMoreTransactions.shouldBeFalse()
      }
    }

    // Test list of 4 transactions, last 3 are confirmed
    transactionsActivityService.transactionsState.value = TransactionsActivityState.Loaded(
      listOf(
        BitcoinTransactionFake,
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake))
      ).map(::BitcoinWalletTransaction)
    )
    stateMachine.test(createProps(transactionVisibility)) {
      awaitItem().shouldNotBeNull().should {
        it.listModel.header.shouldBeNull()
        it.listModel.items.shouldHaveSize(numberOfVisibleTransactions)
        it.hasMoreTransactions.shouldBeTrue()
      }
    }

    // We always show pending over confirmed in Some visibility, so pending should show even though it's last.
    transactionsActivityService.transactionsState.value = TransactionsActivityState.Loaded(
      listOf(
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake
      ).map(::BitcoinWalletTransaction)
    )
    stateMachine.test(createProps(transactionVisibility)) {
      awaitItem().shouldNotBeNull().should {
        it.listModel.header.shouldBeNull()
        it.listModel.items.shouldHaveSize(numberOfVisibleTransactions)
        it.hasMoreTransactions.shouldBeTrue()
      }
    }

    // Test list of 4 transactions, first 3 are pending
    transactionsActivityService.transactionsState.value = TransactionsActivityState.Loaded(
      listOf(
        BitcoinTransactionFake,
        BitcoinTransactionFake,
        BitcoinTransactionFake,
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake))
      ).map(::BitcoinWalletTransaction)
    )
    stateMachine.test(createProps(transactionVisibility)) {
      awaitItem().shouldNotBeNull().should {
        it.listModel.header.shouldBeNull()
        it.listModel.items.shouldHaveSize(numberOfVisibleTransactions)
        it.hasMoreTransactions.shouldBeTrue()
      }
    }

    // We always show pending over confirmed in Some visibility, so pending should show even though it's last.
    transactionsActivityService.transactionsState.value = TransactionsActivityState.Loaded(
      listOf(
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake,
        BitcoinTransactionFake,
        BitcoinTransactionFake
      ).map(::BitcoinWalletTransaction)
    )
    stateMachine.test(createProps(transactionVisibility)) {
      awaitItem().shouldNotBeNull().should {
        it.listModel.header.shouldBeNull()
        it.listModel.items.shouldHaveSize(numberOfVisibleTransactions)
        it.hasMoreTransactions.shouldBeTrue()
      }
    }
  }
})
