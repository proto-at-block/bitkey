package build.wallet.statemachine.transactions

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.blockchain.BitcoinBlockchainMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailRepositoryMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.SpeedUpTransactionDetails
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.ExchangeRateSyncerMock
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FeeBumpConfirmationUiStateMachineImplTests : FunSpec({

  val spendingWallet = SpendingWalletMock(turbines::create)
  val bitcoinBlockchain = BitcoinBlockchainMock(turbines::create)
  val outgoingTransactionDetailRepository =
    OutgoingTransactionDetailRepositoryMock(turbines::create)

  val stateMachine = FeeBumpConfirmationUiStateMachineImpl(
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
    transactionDetailsCardUiStateMachine = object : TransactionDetailsCardUiStateMachine,
      StateMachineMock<TransactionDetailsCardUiProps, TransactionDetailsModel>(
        initialModel =
          TransactionDetailsModel(
            transactionSpeedText = "transactionSpeedText",
            transactionDetailModelType =
              TransactionDetailModelType.Regular(
                transferAmountText = "transferFiatAmountText",
                feeAmountText = "feeFiatAmountText",
                totalAmountPrimaryText = "totalFiatAmountText",
                totalAmountSecondaryText = "totalBitcoinAmountText"
              )
          )
      ) {},
    exchangeRateSyncer = ExchangeRateSyncerMock(turbines::create),
    nfcSessionUIStateMachine = object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {},
    bitcoinBlockchain = bitcoinBlockchain,
    outgoingTransactionDetailRepository = outgoingTransactionDetailRepository,
    transferInitiatedUiStateMachine = object : TransferInitiatedUiStateMachine,
      BodyStateMachineMock<TransferInitiatedUiProps>(
        "transfer-initiated"
      ) {}
  )

  val props = FeeBumpConfirmationProps(
    account = FullAccountMock,
    speedUpTransactionDetails = SpeedUpTransactionDetails(
      txid = "1234",
      recipientAddress = BitcoinAddress("1234"),
      sendAmount = BitcoinMoney.sats(10000),
      oldFee = Fee(BitcoinMoney.Companion.sats(2000), feeRate = FeeRate(1f))
    ),
    onExit = {},
    syncTransactions = {
      spendingWallet.sync()
    },
    newFeeRate = FeeRate(2f),
    psbt = PsbtMock
  )

  test("fee bump happy path") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        primaryButton
          .shouldNotBeNull()
          .onClick()
      }

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Psbt>>("nfc") {
        onSuccess(PsbtMock)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel>()

      bitcoinBlockchain.broadcastCalls.awaitItem().shouldBe(PsbtMock)

      outgoingTransactionDetailRepository.setTransactionCalls.awaitItem().shouldBe(Unit)

      awaitScreenWithBodyModelMock<TransferInitiatedUiProps>("transfer-initiated") {
        onDone()
      }
    }
  }
})
