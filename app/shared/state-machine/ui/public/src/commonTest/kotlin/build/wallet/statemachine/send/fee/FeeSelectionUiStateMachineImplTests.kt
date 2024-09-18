package build.wallet.statemachine.send.fee

import app.cash.turbine.plusAssign
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.address.bitcoinAddressP2WPKH
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.CannotCreatePsbtError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.InsufficientFundsError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.SpendingBelowDustLimitError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimatorMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.oneSatPerVbyteFeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.bitcoin.transactions.KeyboxTransactionsDataMock
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitcoin.transactions.TransactionsServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList.FeeOption
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_BELOW_DUST_LIMIT_ERROR_SCREEN
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_PSBT_CONSTRUCTION_ERROR_SCREEN
import build.wallet.statemachine.ui.clickPrimaryButton
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FeeSelectionUiStateMachineImplTests : FunSpec({
  val bitcoinTransactionFeeEstimator =
    BitcoinTransactionFeeEstimatorMock()
  val transactionPriorityPreference = TransactionPriorityPreferenceFake()
  val feeOptionListUiStateMachine = FeeOptionListUiStateMachineFake()
  val bitcoinTransactionBaseCalculator = BitcoinTransactionBaseCalculatorMock(BitcoinMoney.zero())
  val transactionsService = TransactionsServiceFake()
  val accountService = AccountServiceFake()

  val stateMachine =
    FeeSelectionUiStateMachineImpl(
      bitcoinTransactionFeeEstimator = bitcoinTransactionFeeEstimator,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = feeOptionListUiStateMachine,
      transactionBaseCalculator = bitcoinTransactionBaseCalculator,
      transactionsService = transactionsService,
      accountService = accountService
    )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onContinueCalls = turbines.create<EstimatedTransactionPriority>("on continue calls")

  val props =
    FeeSelectionUiProps(
      recipientAddress = bitcoinAddressP2WPKH,
      sendAmount = ExactAmount(BitcoinMoney.zero()),
      exchangeRates = immutableListOf(),
      onBack = { onBackCalls += Unit },
      onContinue = { priority, _ -> onContinueCalls += priority }
    )

  beforeTest {
    transactionsService.reset()
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)

    transactionsService.transactionsData.value = KeyboxTransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(10.0))
    )
  }

  afterTest {
    bitcoinTransactionFeeEstimator.feesResult =
      Ok(
        mapOf(
          FASTEST to Fee(BitcoinMoney.btc(10.0), oneSatPerVbyteFeeRate),
          THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0), oneSatPerVbyteFeeRate),
          SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
        )
      )
    transactionPriorityPreference.preference = null
    bitcoinTransactionBaseCalculator.minimumSatsRequired = BitcoinMoney.zero()
  }

  test("option list is created and can select an option") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Select a transfer speed")
        primaryButton.shouldNotBeNull().text.shouldBe("Continue")

        with(mainContentList.first().shouldBeInstanceOf<FeeOptionList>()) {
          options.size.shouldBe(3)
          options[0].verifyFeeOption(
            selected = false
          )
          options[1].verifyFeeOption(
            selected = true
          )
          options[2].verifyFeeOption(
            selected = false
          )
          clickPrimaryButton()
        }
      }

      onContinueCalls.awaitItem().shouldBe(THIRTY_MINUTES)
    }
  }

  test("when no fees are estimated, onContinue is called with default") {
    bitcoinTransactionFeeEstimator.feesResult = Ok(emptyMap())
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      onContinueCalls.awaitItem().shouldBe(THIRTY_MINUTES)
    }
  }

  test("when there is a priority preference, that value is the default selected") {
    transactionPriorityPreference.preference = FASTEST
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<FeeOptionList>()) {
          options[0].verifyFeeOption(
            selected = true
          )
          options[1].verifyFeeOption(
            selected = false
          )
          options[2].verifyFeeOption(
            selected = false
          )
        }
      }
    }
  }

  test("when there is a priority preference but there is no fee the default is selected") {
    transactionPriorityPreference.preference = FASTEST
    bitcoinTransactionFeeEstimator.feesResult =
      Ok(
        mapOf(
          THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0), oneSatPerVbyteFeeRate),
          SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
        )
      )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<FeeOptionList>()) {
          options[0].verifyFeeOption(
            selected = true
          )
          options[1].verifyFeeOption(
            selected = false
          )
        }
      }
    }
  }

  test("continue button is disabled when none are selected") {
    transactionPriorityPreference.preference = FASTEST
    bitcoinTransactionFeeEstimator.feesResult =
      Ok(
        mapOf(
          SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
        )
      )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<FeeOptionList>()) {
          options[0].verifyFeeOption(
            selected = false
          )
        }

        primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
      }
    }
  }

  test("when balance is below transaction + fees,  show insufficient funds screen") {
    bitcoinTransactionBaseCalculator.minimumSatsRequired = BitcoinMoney.btc(1.01)
    transactionsService.transactionsData.value = KeyboxTransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(1.0))
    )

    stateMachine.test(props.copy(sendAmount = ExactAmount(BitcoinMoney.btc(1.1)))) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      } // loading state
      awaitBody<FormBodyModel> {
        id.shouldBe(FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN)
      }
    }
  }

  test("onBack is called") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        onBack?.invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("when all fees are the same, FASTEST priority is selected") {
    transactionPriorityPreference.preference = SIXTY_MINUTES
    bitcoinTransactionFeeEstimator.feesResult =
      Ok(
        mapOf(
          FASTEST to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate),
          THIRTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate),
          SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
        )
      )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      onContinueCalls.awaitItem().shouldBe(FASTEST)
    }
  }

  test("when transaction (with fee) exceeds balance, show correct error screen") {
    bitcoinTransactionFeeEstimator.feesResult = Err(CannotCreatePsbtError(null))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t send this transaction")
      }
    }
  }

  test("when transaction (with fee) exceeds balance, show error screen") {
    bitcoinTransactionFeeEstimator.feesResult = Err(InsufficientFundsError)

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        id.shouldBe(FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN)
      }
    }
  }

  test("when transaction is below dust limit, show error screen") {
    bitcoinTransactionFeeEstimator.feesResult = Err(SpendingBelowDustLimitError)

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        id.shouldBe(FEE_ESTIMATION_BELOW_DUST_LIMIT_ERROR_SCREEN)
      }
    }
  }

  test("if we have trouble constructing a PSBT, show error screen") {
    bitcoinTransactionFeeEstimator.feesResult = Err(CannotCreatePsbtError("generic message"))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        id.shouldBe(FEE_ESTIMATION_PSBT_CONSTRUCTION_ERROR_SCREEN)
      }
    }
  }

  test("show error screen when we do not have sufficient funds to send all") {
    bitcoinTransactionBaseCalculator.minimumSatsRequired = BitcoinMoney.btc(-1.00)

    stateMachine.test(
      props.copy(
        sendAmount = SendAll
      )
    ) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        id.shouldBe(FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN)
      }
    }
  }
})

private fun FeeOption.verifyFeeOption(selected: Boolean) {
  this.selected.shouldBe(selected)
}
