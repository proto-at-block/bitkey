package build.wallet.statemachine.transactions

import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.currency.USD
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.transactions.TransactionListUiProps.TransactionVisibility
import build.wallet.statemachine.transactions.TransactionListUiProps.TransactionVisibility.All
import build.wallet.statemachine.transactions.TransactionListUiProps.TransactionVisibility.Some
import build.wallet.ui.model.list.ListItemModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class TransactionListStateMachineImplTests : FunSpec({

  val stateMachine =
    TransactionListUiStateMachineImpl(
      transactionItemUiStateMachine =
        object : TransactionItemUiStateMachine,
          StateMachineMock<TransactionItemUiProps, ListItemModel>(
            initialModel =
              ListItemModel(
                title = "transactionId",
                secondaryText = "date",
                sideText = "amount",
                secondarySideText = "amountEquivalent",
                onClick = {}
              )
          ) {}
    )

  test("model with All visibility, all pending") {
    stateMachine.test(createProps(transactionVisibility = All, transactions = txnListAllPending)) {
      awaitItem().shouldNotBeNull().should {
        it.size.shouldBe(1)
        it.first().header.shouldBe(null)
        it.first().items.size.shouldBe(10)
      }
    }
  }

  test("model with All visibility, all confirmed") {
    stateMachine.test(
      createProps(transactionVisibility = All, transactions = txnListAllConfirmed)
    ) {
      awaitItem().shouldNotBeNull().should {
        it.size.shouldBe(1)
        it.first().header.shouldBeNull()
        it.first().items.size.shouldBe(10)
      }
    }
  }

  test("model with All visibility, half pending half confirmed") {
    stateMachine.test(
      createProps(
        transactionVisibility = All,
        transactions = (txnListAllPending + txnListAllConfirmed).toImmutableList()
      )
    ) {
      awaitItem().shouldNotBeNull().should {
        it.size.shouldBe(2)
        it[0].header.shouldBe(null)
        it[0].items.size.shouldBe(10)
        it[1].header.shouldBe(null)
        it[1].items.size.shouldBe(10)
      }
    }
  }

  test("model with Some visibility, all pending") {
    val numberOfVisibleTransactions = 3
    stateMachine.test(
      createProps(
        transactionVisibility = Some(numberOfVisibleTransactions),
        transactions = txnListAllPending
      )
    ) {
      awaitItem().shouldNotBeNull().should {
        it.size.shouldBe(1)
        it.first().header.shouldBe(null)
        it.first().items.size.shouldBe(numberOfVisibleTransactions)
      }
    }
  }

  test("model with Some visibility, all confirmed") {
    val numberOfVisibleTransactions = 3
    stateMachine.test(
      createProps(
        transactionVisibility = Some(numberOfVisibleTransactions),
        transactions = txnListAllConfirmed
      )
    ) {
      awaitItem().shouldNotBeNull().should {
        it.size.shouldBe(1)
        it.first().header.shouldBeNull()
        it.first().items.size.shouldBe(numberOfVisibleTransactions)
      }
    }
  }

  test("model with Some visibility, mix of pending and confirmed") {
    val numberOfVisibleTransactions = 3
    val transactionVisibility = Some(numberOfVisibleTransactions)

    // Test list of 1 transaction
    with(immutableListOf(BitcoinTransactionFake)) {
      stateMachine.test(createProps(transactionVisibility, transactions = this)) {
        awaitItem().shouldNotBeNull().should {
          it.size.shouldBe(1)
          it[0].header.shouldBe(null)
          it[0].items.size.shouldBe(1)
        }
      }
    }

    // Test list of 4 transactions, last 3 are confirmed
    with(
      immutableListOf(
        BitcoinTransactionFake,
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake))
      )
    ) {
      stateMachine.test(createProps(transactionVisibility, transactions = this)) {
        awaitItem().shouldNotBeNull().should {
          it.size.shouldBe(2)
          it[0].header.shouldBe(null)
          it[0].items.size.shouldBe(1)
          it[1].header.shouldBe(null)
          it[1].items.size.shouldBe(2)
        }
      }
    }

    // Test list of 4 transactions, first 3 are confirmed
    // We always show pending over confirmed in Some visibility, so pending should show even though it's last.
    with(
      immutableListOf(
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake
      )
    ) {
      stateMachine.test(createProps(transactionVisibility, transactions = this)) {
        awaitItem().shouldNotBeNull().should {
          it.size.shouldBe(2)
          it[0].header.shouldBe(null)
          it[0].items.size.shouldBe(1)
          it[1].header.shouldBe(null)
          it[1].items.size.shouldBe(2)
        }
      }
    }

    // Test list of 4 transactions, first 3 are pending
    with(
      immutableListOf(
        BitcoinTransactionFake,
        BitcoinTransactionFake,
        BitcoinTransactionFake,
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake))
      )
    ) {
      stateMachine.test(createProps(transactionVisibility, transactions = this)) {
        awaitItem().shouldNotBeNull().should {
          it.size.shouldBe(1)
          it[0].header.shouldBe(null)
          it[0].items.size.shouldBe(3)
        }
      }
    }

    // Test list of 4 transactions, last 3 are pending
    // We always show pending over confirmed in Some visibility, so pending should show even though it's last.
    with(
      immutableListOf(
        BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake)),
        BitcoinTransactionFake,
        BitcoinTransactionFake,
        BitcoinTransactionFake
      )
    ) {
      stateMachine.test(createProps(transactionVisibility, transactions = this)) {
        awaitItem().shouldNotBeNull().should {
          it.size.shouldBe(1)
          it[0].header.shouldBe(null)
          it[0].items.size.shouldBe(3)
        }
      }
    }
  }
})

private fun createProps(
  transactionVisibility: TransactionVisibility,
  transactions: ImmutableList<BitcoinTransaction>,
): TransactionListUiProps {
  return TransactionListUiProps(
    transactionVisibility = transactionVisibility,
    transactions = transactions,
    fiatCurrency = USD,
    onTransactionClicked = {}
  )
}

// List of 10 transactions, all pending
private val txnListAllPending =
  List(10) { BitcoinTransactionFake }
    .toImmutableList()

// List of 10 transactions, all confirmed
private val txnListAllConfirmed =
  List(10) {
    BitcoinTransactionFake.copy(confirmationStatus = Confirmed(BlockTimeFake))
  }.toImmutableList()
