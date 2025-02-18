package build.wallet.statemachine.transactions

import app.cash.turbine.test
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.SpeedUpTransactionDetails
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.*
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpConfirmationModel
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpTransactionSentModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly

class FeeBumpConfirmationUiStateMachineImplTests : FunSpec({

  val spendingWallet = SpendingWalletMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()

  val stateMachine = FeeBumpConfirmationUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = object : TransactionDetailsCardUiStateMachine,
      StateMachineMock<TransactionDetailsCardUiProps, TransactionDetailsModel>(
        initialModel =
          TransactionDetailsModel(
            transactionSpeedText = "transactionSpeedText",
            transactionDetailModelType =
              TransactionDetailModelType.SpeedUp(
                transferAmountText = "transferAmountText",
                transferAmountSecondaryText = "transferAmountBtcText",
                oldFeeAmountText = "oldFeeAmountText",
                oldFeeAmountSecondaryText = "oldFeeAmountBtcText",
                feeDifferenceText = "feeDifferenceText",
                feeDifferenceSecondaryText = "feeDifferenceBtcText",
                totalAmountPrimaryText = "totalFiatAmountText",
                totalAmountSecondaryText = "totalBitcoinAmountText",
                totalFeeText = "totalFeeText",
                totalFeeSecondaryText = "totalFeeBtcText"
              )
          )
      ) {},
    exchangeRateService = ExchangeRateServiceFake(),
    nfcSessionUIStateMachine = object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {},
    transferInitiatedUiStateMachine = object : TransferInitiatedUiStateMachine,
      BodyStateMachineMock<TransferInitiatedUiProps>(
        "transfer-initiated"
      ) {},
    bitcoinWalletService = bitcoinWalletService
  )

  val props = FeeBumpConfirmationProps(
    account = FullAccountMock,
    speedUpTransactionDetails = SpeedUpTransactionDetails(
      txid = "1234",
      recipientAddress = BitcoinAddress("1234"),
      sendAmount = BitcoinMoney.sats(10000),
      oldFee = Fee(BitcoinMoney.sats(2000), feeRate = FeeRate(1f)),
      transactionType = Outgoing
    ),
    onExit = {},
    newFeeRate = FeeRate(2f),
    psbt = PsbtMock
  )

  beforeTest {
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = spendingWallet
  }

  test("fee bump happy path") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<TransferConfirmationScreenModel> {
        onConfirmClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<Psbt>>("nfc") {
        shouldShowLongRunningOperation.shouldBeTrue()
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitItem().shouldContainExactly(PsbtMock)
      }

      awaitBodyMock<TransferInitiatedUiProps>("transfer-initiated") {
        onDone()
      }
    }
  }

  test("fee bump for utxo consolidation happy path") {
    stateMachine.testWithVirtualTime(
      props.copy(
        speedUpTransactionDetails = props.speedUpTransactionDetails.copy(
          transactionType = UtxoConsolidation
        )
      )
    ) {
      awaitBody<UtxoConsolidationSpeedUpConfirmationModel> {
        onConfirmClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<Psbt>>("nfc") {
        onSuccess(PsbtMock)
      }

      awaitBody<LoadingSuccessBodyModel>()

      bitcoinWalletService.broadcastedPsbts.test {
        awaitItem().shouldContainExactly(PsbtMock)
      }

      awaitBody<UtxoConsolidationSpeedUpTransactionSentModel> {
        onDone()
      }
    }
  }
})
