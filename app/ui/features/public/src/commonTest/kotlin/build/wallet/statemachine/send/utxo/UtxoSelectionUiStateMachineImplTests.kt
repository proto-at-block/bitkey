package build.wallet.statemachine.send.utxo

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bdk.bindings.BdkUtxoMock2
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitcoin.utxo.CoinControl
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.model.list.ListItemAccessory.CheckboxAccessory
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class UtxoSelectionUiStateMachineImplTests : FunSpec({
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val stateMachine = UtxoSelectionUiStateMachineImpl(
    bitcoinWalletService = bitcoinWalletService,
    moneyDisplayFormatter = MoneyDisplayFormatterFake
  )

  val utxoA = BdkUtxoMock
  val utxoB = BdkUtxoMock2.copy(outPoint = BdkOutPoint(txid = "def", vout = 1u))
  val inventory = setOf(utxoA, utxoB)

  val onConfirmCalls = turbines.create<CoinControl>("onConfirm")
  val onClearCalls = turbines.create<Unit>("onClear")
  val onBackCalls = turbines.create<Unit>("onBack")

  beforeTest {
    bitcoinWalletService.reset()
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      utxos = Utxos(confirmed = inventory, unconfirmed = emptySet())
    )
  }

  fun props(
    targetAmount: BitcoinMoney? = BitcoinMoney.sats(1),
    initialSelection: CoinControl? = null,
  ) = UtxoSelectionUiProps(
    targetAmount = targetAmount,
    initialSelection = initialSelection,
    onConfirm = { onConfirmCalls.add(it) },
    onClear = { onClearCalls.add(Unit) },
    onBack = { onBackCalls.add(Unit) }
  )

  test("lists confirmed utxos and confirms selection") {
    stateMachine.test(props()) {
      awaitBody<FormBodyModel> {
        val listGroup = mainContentList.first().shouldBeInstanceOf<ListGroup>()
        listGroup.listGroupModel.items.shouldHaveSize(2)
        primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()

        val firstCheckbox = listGroup.listGroupModel.items.first()
          .leadingAccessory.shouldBeInstanceOf<CheckboxAccessory>()
        firstCheckbox.onClick()
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
        primaryButton!!.onClick()
      }

      val confirmed = onConfirmCalls.awaitItem()
      confirmed.count shouldBe 1
    }
  }

  test("shows soft underfunded messaging for ExactAmount") {
    stateMachine.test(props(targetAmount = BitcoinMoney.sats(100))) {
      awaitBody<FormBodyModel> {
        val listGroup = mainContentList.first().shouldBeInstanceOf<ListGroup>()
        listGroup.listGroupModel.items.first()
          .leadingAccessory.shouldBeInstanceOf<CheckboxAccessory>()
          .onClick()
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string
          .shouldContain("less than the send amount")
        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
      }
    }
  }

  test("clear invokes onClear") {
    val initial = CoinControl.create(
      inventory = inventory,
      selected = setOf(utxoA.outPoint)
    ).get().shouldNotBeNull()

    stateMachine.test(props(initialSelection = initial)) {
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }
      onClearCalls.awaitItem()
    }
  }

  test("back invokes onBack") {
    stateMachine.test(props()) {
      awaitBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }
      onBackCalls.awaitItem()
    }
  }
})
