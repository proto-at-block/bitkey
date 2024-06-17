package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.blockchain.BitcoinBlockchainMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.fees.oneSatPerVbyteFeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailRepositoryMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientMock
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.keybox.transactions.KeyboxTransactionsDataMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.TransferConfirmationUiProps.Variant
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachineFake
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.icon.IconImage
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.immutableMapOf
import kotlinx.collections.immutable.persistentMapOf

class TransferConfirmationUiStateMachineImplTests : FunSpec({

  val onTransferInitiatedCalls =
    turbines.create<Psbt>(
      "transfer initiated calls"
    )
  val onTransferFailedCalls = turbines.create<Unit>("transfer failed calls")
  val onBackCalls = turbines.create<Unit>("back calls")
  val onExitCalls = turbines.create<Unit>("exit calls")
  val appSignedPsbt = PsbtMock.copy(id = "app-signed-psbt")
  val appAndHwSignedPsbt =
    PsbtMock.copy(
      id = "app-and-hw-signed-psbt"
    )
  val appAndServerSignedPsbt =
    PsbtMock.copy(
      id = "app-and-server-signed-psbt"
    )

  val transactionsSyncCalls = turbines.create<Unit>("sync calls")
  val keyboxData =
    ActiveKeyboxLoadedDataMock.copy(
      transactionsData =
        KeyboxTransactionsDataMock.copy(
          syncTransactions = { transactionsSyncCalls += Unit }
        )
    )
  val transactionDetailsCardUiStateMachine =
    object : TransactionDetailsCardUiStateMachine,
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
      ) {}
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  @Suppress("DEPRECATION")
  val props =
    TransferConfirmationUiProps(
      transferVariant = Variant.Regular(THIRTY_MINUTES),
      accountData = keyboxData,
      recipientAddress = someBitcoinAddress,
      sendAmount = ExactAmount(BitcoinMoney.sats(123_456)),
      requiredSigner = SigningFactor.Hardware,
      spendingLimit = null,
      fees = immutableMapOf(
        FASTEST to Fee(BitcoinMoney.btc(10.0), oneSatPerVbyteFeeRate),
        THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0), oneSatPerVbyteFeeRate),
        SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
      ),
      exchangeRates = emptyImmutableList(),
      onTransferInitiated = { psbt, _ -> onTransferInitiatedCalls.add(psbt) },
      onTransferFailed = { onTransferFailedCalls.add(Unit) },
      onBack = { onBackCalls.add(Unit) },
      onExit = { onExitCalls.add(Unit) }
    )

  val bitcoinBlockchain = BitcoinBlockchainMock(turbines::create)
  val serverSigner = MobilePaySigningF8eClientMock(turbines::create)
  val transactionPriorityPreference = TransactionPriorityPreferenceFake()
  val spendingWallet = SpendingWalletMock(turbines::create)
  val appSpendingWalletProvider = AppSpendingWalletProviderMock(spendingWallet)
  val transactionRepository = OutgoingTransactionDetailRepositoryMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val stateMachine =
    TransferConfirmationUiStateMachineImpl(
      mobilePaySigningF8eClient = serverSigner,
      bitcoinBlockchain = bitcoinBlockchain,
      transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      transactionPriorityPreference = transactionPriorityPreference,
      feeOptionListUiStateMachine = FeeOptionListUiStateMachineFake(),
      appSpendingWalletProvider = appSpendingWalletProvider,
      outgoingTransactionDetailRepository = transactionRepository,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
    )

  beforeTest {
    bitcoinBlockchain.reset()
    serverSigner.reset()
    spendingWallet.reset()
    transactionPriorityPreference.reset()
  }

  test("create unsigned psbt error - insufficent funds") {
    spendingWallet.createSignedPsbtResult =
      Err(BdkError.InsufficientFunds(Exception(""), null))

    stateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Error screen
      awaitScreenWithBody<FormBodyModel> {
        with(header.shouldNotBeNull()) {
          headline.shouldBe("We couldn’t send this transaction")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "The amount you are trying to send is too high. Please decrease the amount and try again."
          )
        }
        with(primaryButton.shouldNotBeNull()) {
          text.shouldBe("Go Back")
          onClick()
        }
      }
      onBackCalls.awaitItem()
    }
  }

  test("create unsigned psbt error - other error") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))

    stateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }
  }

  test("[app & hw] failure to sign with app key presents error message") {
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))
    transactionPriorityPreference.preference.shouldBeNull()

    stateMachine.test(props.copy(requiredSigner = SigningFactor.Hardware)) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("[app & hw] successful signing syncs, broadcasts, calls onTransferInitiated") {
    val transactionPriority = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)

    stateMachine.test(
      props.copy(
        requiredSigner = SigningFactor.Hardware,
        transferVariant = Variant.Regular(transactionPriority)
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().iconModel.shouldNotBeNull().iconImage.shouldBe(
          IconImage.LocalImage(
            Icon.Bitcoin
          )
        )
        header.shouldNotBeNull().headline.shouldBe("Send your transfer")

        // Correct title
        mainContentList[0]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items[0]
          .title.shouldBe("Arrival time")

        // Only show transfer amount and fee.
        mainContentList[1]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items.size.shouldBe(2)

        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinBlockchain.broadcastCalls.awaitItem().shouldBe(appAndHwSignedPsbt)

      // We persist this transaction into the database
      transactionRepository.setTransactionCalls.awaitItem()
    }

    transactionsSyncCalls.awaitItem()
    transactionPriorityPreference.preference.shouldBe(transactionPriority)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[app & hw] successfully signing, but failing to broadcast presents error") {
    val transactionPriority = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    bitcoinBlockchain.broadcastResult = Err(BdkError.Generic(Exception(""), null))

    stateMachine.test(
      props.copy(
        requiredSigner = SigningFactor.Hardware,
        transferVariant = Variant.Regular(transactionPriority)
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      bitcoinBlockchain.broadcastCalls.awaitItem().shouldBe(appAndHwSignedPsbt)

      // ReceivedBdkError
      awaitScreenWithBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("[app & server] successful signing syncs, broadcasts, calls onTransferInitiated") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    serverSigner.signWithSpecificKeysetResult = Ok(appAndServerSignedPsbt)

    transactionPriorityPreference.preference.shouldBeNull()

    stateMachine.test(
      props.copy(
        requiredSigner = SigningFactor.F8e,
        spendingLimit = SpendingLimitMock,
        transferVariant = Variant.Regular(preferenceToSet)
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      serverSigner.signWithSpecificKeysetCalls.awaitItem().shouldBe(appSignedPsbt)
      bitcoinBlockchain.broadcastCalls.awaitItem().shouldBe(appAndServerSignedPsbt)

      // We persist this transaction into the database
      transactionRepository.setTransactionCalls.awaitItem()
    }

    transactionsSyncCalls.awaitItem()
    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[app & server] failure to sign with app key presents error") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Err(BdkError.Generic(Exception(""), null))
    serverSigner.signWithSpecificKeysetResult = Ok(appAndServerSignedPsbt)

    stateMachine.test(
      props.copy(
        requiredSigner = SigningFactor.F8e,
        spendingLimit = SpendingLimitMock,
        transferVariant = Variant.Regular(preferenceToSet)
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ReceivedBdkError
      awaitScreenWithBody<FormBodyModel> {
        expectGenericErrorMessage()
        clickPrimaryButton()
      }
      onExitCalls.awaitItem()
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("[app & server] successfully signing, but failing to broadcast succeeds") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    serverSigner.signWithSpecificKeysetResult = Ok(appAndServerSignedPsbt)
    bitcoinBlockchain.broadcastResult = Err(BdkError.Generic(Exception(""), null))

    stateMachine.test(
      props.copy(
        requiredSigner = SigningFactor.F8e,
        spendingLimit = SpendingLimitMock,
        transferVariant = Variant.Regular(preferenceToSet)
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      serverSigner.signWithSpecificKeysetCalls.awaitItem().shouldBe(appSignedPsbt)
      bitcoinBlockchain.broadcastCalls.awaitItem().shouldBe(appAndServerSignedPsbt)
    }

    transactionsSyncCalls.awaitItem()
    transactionPriorityPreference.preference.shouldBe(preferenceToSet)
    onTransferInitiatedCalls.awaitItem()
  }

  test("[app & server] failure to sign with server key presents error") {
    val preferenceToSet = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)
    serverSigner.signWithSpecificKeysetResult = Err(NetworkError(Throwable()))

    stateMachine.test(
      props.copy(
        requiredSigner = SigningFactor.F8e,
        spendingLimit = SpendingLimitMock,
        transferVariant = Variant.Regular(preferenceToSet)
      )
    ) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // SigningWithServer
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      serverSigner.signWithSpecificKeysetCalls.awaitItem().shouldBe(appSignedPsbt)

      // ReceivedServerSigningError
      awaitScreenWithBody<FormBodyModel> {
        with(header.shouldNotBeNull()) {
          headline.shouldBe("We couldn’t send this as a mobile-only transaction")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "Please use your hardware device to confirm this transaction."
          )
        }
        with(primaryButton.shouldNotBeNull()) {
          text.shouldBe("Continue")
          onClick()
        }
      }

      // SigningWithHardware
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachine.id
      )
    }

    transactionPriorityPreference.preference.shouldBeNull()
  }

  test("Transaction details update after selecting a new fee from sheet") {
    stateMachine.test(props) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        transactionDetailsCardUiStateMachine.props
          .transactionDetail
          .feeBitcoinAmount
          .shouldBe(BitcoinMoney.btc(2.0))

        with(mainContentList[0].shouldBeTypeOf<DataList>()) {
          items[0].onClick.shouldNotBeNull().invoke()
        }
      }

      with(awaitItem().bottomSheetModel.shouldNotBeNull()) {
        with(body.shouldBeTypeOf<FormBodyModel>()) {
          mainContentList[0].shouldBeTypeOf<FeeOptionList>()
            .options[0]
            .onClick
            .shouldNotBeNull()
            .invoke()
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        transactionDetailsCardUiStateMachine.props
          .transactionDetail
          .feeBitcoinAmount
          .shouldBe(BitcoinMoney.btc(BigDecimal.TEN))
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  context("Fee bump transactions") {
    val feeBumpProps =
      TransferConfirmationUiProps(
        transferVariant =
          Variant.SpeedUp(
            txid = "abc",
            oldFee = Fee(BitcoinMoney.sats(256), FeeRate(1f)),
            newFeeRate = FeeRate(2f)
          ),
        accountData = keyboxData,
        recipientAddress = someBitcoinAddress,
        sendAmount = ExactAmount(BitcoinMoney.sats(123_456)),
        requiredSigner = SigningFactor.Hardware,
        spendingLimit = null,
        fees = persistentMapOf(
          FASTEST to Fee(BitcoinMoney.btc(10.0), oneSatPerVbyteFeeRate),
          THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0), oneSatPerVbyteFeeRate),
          SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0), oneSatPerVbyteFeeRate)
        ),
        exchangeRates = emptyImmutableList(),
        onTransferInitiated = { psbt, _ -> onTransferInitiatedCalls.add(psbt) },
        onTransferFailed = { onTransferFailedCalls.add(Unit) },
        onBack = { onBackCalls.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      )

    transactionDetailsCardUiStateMachine.emitModel(
      TransactionDetailsModel(
        transactionDetailModelType =
          TransactionDetailModelType.SpeedUp(
            transferAmountText = "transferAmountText",
            oldFeeAmountText = "oldFeeAmountText",
            feeDifferenceText = "feeDifferenceText",
            totalAmountPrimaryText = "totalFiatAmountText",
            totalAmountSecondaryText = "totalBitcoinAmountText"
          ),
        transactionSpeedText = "transactionSpeedText"
      )
    )

    stateMachine.test(feeBumpProps) {
      // CreatingAppSignedPsbt
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // ViewingTransferConfirmation
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().iconModel.shouldNotBeNull().iconImage.shouldBe(
          IconImage.LocalImage(
            Icon.LargeIconSpeedometer
          )
        )
        header.shouldNotBeNull().headline.shouldBe("Speed up your transfer to")

        // Correct title
        mainContentList[0]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items[0]
          .title.shouldBe("New arrival time")

        // Shows transfer amount, old fee, new fee delta.
        mainContentList[1]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items.size.shouldBe(3)
      }
    }
  }
})

private fun FormBodyModel.expectGenericErrorMessage() {
  with(header.shouldNotBeNull()) {
    headline.shouldBe("We couldn’t send this transaction")
    sublineModel.shouldNotBeNull().string.shouldBe(
      "We are looking into this. Please try again later."
    )
  }
  with(primaryButton.shouldNotBeNull()) {
    text.shouldBe("Done")
  }
}
