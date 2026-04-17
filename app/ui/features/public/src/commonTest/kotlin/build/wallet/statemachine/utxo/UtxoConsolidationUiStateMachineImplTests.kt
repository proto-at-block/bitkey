package build.wallet.statemachine.utxo

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.UTXO_CONSOLIDATION_SIGN_TRANSACTION
import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.utxo.NotEnoughUtxosToConsolidateError
import build.wallet.bitcoin.utxo.UtxoConsolidationContext
import build.wallet.bitcoin.utxo.UtxoConsolidationParams
import build.wallet.bitcoin.utxo.UtxoConsolidationServiceFake
import build.wallet.bitcoin.utxo.UtxoConsolidationType
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiProps
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiStateMachineMock
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UtxoConsolidationUiStateMachineImplTests : FunSpec({
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val currencyConverter = CurrencyConverterFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val dateTimeFormatter = DateTimeFormatterMock()
  val timeZoneProvider = TimeZoneProviderMock()
  val utxoConsolidationService = UtxoConsolidationServiceFake()
  val signTransactionNfcSessionUiStateMachine = SignTransactionNfcSessionUiStateMachineMock("sign-txn-nfc")

  val stateMachine = UtxoConsolidationUiStateMachineImpl(
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    currencyConverter = currencyConverter,
    moneyDisplayFormatter = moneyDisplayFormatter,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider,
    utxoConsolidationService = utxoConsolidationService,
    signTransactionNfcSessionUiStateMachine = signTransactionNfcSessionUiStateMachine
  )

  val props = UtxoConsolidationProps(
    account = FullAccountMock,
    onConsolidationSuccess = {},
    onBack = {}
  )

  beforeTest {
    utxoConsolidationService.reset()
  }

  test("happy path") {
    stateMachine.test(props) {
      // Loading the consolidation psbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Confirmation screen.
      // Emits twice due to currency conversion.
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        balanceTitle.shouldBe("Wallet balance")
        // tap continue
        onContinue.invoke()
      }

      // Tap & Hold info half sheet
      awaitSheet<TapAndHoldToConsolidateUtxosBodyModel> {
        onConsolidate()
      }

      // Nfc signing
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        psbt.shouldBe(PsbtMock)
        eventTrackerContext.shouldBe(UTXO_CONSOLIDATION_SIGN_TRANSACTION)
        onSuccess(PsbtMock) // NB: Psbt doesn't match the consolidation params
      }

      // Broadcasting the psbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // And finally, showing the transaction sent modal
      awaitBody<UtxoConsolidationTransactionSentModel>()
    }
  }

  test("exceeding max utxo count shows max utxo info modal") {
    utxoConsolidationService.prepareUtxoConsolidationResult = Ok(
      listOf(
        UtxoConsolidationParams(
          type = UtxoConsolidationType.ConsolidateAll,
          targetAddress = someBitcoinAddress,
          eligibleUtxoCount = 10,
          balance = sats(1000),
          consolidationCost = sats(5),
          appSignedPsbt = PsbtMock,
          transactionPriority = SIXTY_MINUTES,
          walletHasUnconfirmedUtxos = false,
          walletExceedsMaxUtxoCount = true,
          maxUtxoCount = 5
        )
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        // loading state
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        // Max count info screen
        id.shouldBe(UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT)
        primaryButton.shouldNotBeNull().onClick()
      }

      // Continuing should proceed to confirmation screen
      // Emits twice due to currency conversion.
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        balanceTitle.shouldBe("Value of UTXOs")
      }
    }
  }

  test("private wallet migration context shows exceeds max once then loops back") {
    utxoConsolidationService.prepareUtxoConsolidationResult = Ok(
      listOf(
        UtxoConsolidationParams(
          type = UtxoConsolidationType.ConsolidateAll,
          targetAddress = someBitcoinAddress,
          eligibleUtxoCount = 10,
          balance = sats(1000),
          consolidationCost = sats(5),
          appSignedPsbt = PsbtMock,
          transactionPriority = SIXTY_MINUTES,
          walletHasUnconfirmedUtxos = false,
          walletExceedsMaxUtxoCount = true,
          maxUtxoCount = 5
        )
      )
    )

    val migrationProps = UtxoConsolidationProps(
      account = FullAccountMock,
      onConsolidationSuccess = {},
      onBack = {},
      context = UtxoConsolidationContext.PrivateWalletMigration
    )

    stateMachine.test(migrationProps) {
      // Loading the consolidation psbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // First time: should show exceeds max count screen
      awaitBody<FormBodyModel> {
        id.shouldBe(UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT)
        primaryButton.shouldNotBeNull().onClick()
      }

      // Confirmation screen (emits twice due to currency conversion)
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        balanceTitle.shouldBe("Value of UTXOs")
        onContinue.invoke()
      }

      // Tap & Hold info half sheet
      awaitSheet<TapAndHoldToConsolidateUtxosBodyModel> {
        onConsolidate()
      }

      // Nfc signing
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        psbt.shouldBe(PsbtMock)
        onSuccess(PsbtMock)
      }

      // Broadcasting the psbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Transaction sent modal
      awaitBody<UtxoConsolidationTransactionSentModel> {
        // Tap done to loop back
        onDone()
      }

      // Second consolidation: Loading again
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Second time: should skip exceeds max count screen and go directly to confirmation
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        balanceTitle.shouldBe("Value of UTXOs")
      }
    }
  }

  test("auto-loop gracefully exits when subsequent prep has not enough UTXOs") {
    utxoConsolidationService.prepareUtxoConsolidationResult = Ok(
      listOf(
        UtxoConsolidationParams(
          type = UtxoConsolidationType.ConsolidateAll,
          targetAddress = someBitcoinAddress,
          eligibleUtxoCount = 10,
          balance = sats(1000),
          consolidationCost = sats(5),
          appSignedPsbt = PsbtMock,
          transactionPriority = SIXTY_MINUTES,
          walletHasUnconfirmedUtxos = false,
          walletExceedsMaxUtxoCount = true,
          maxUtxoCount = 5
        )
      )
    )

    val onConsolidationSuccessCalls = turbines.create<Unit>("on-consolidation-success")
    val migrationProps = UtxoConsolidationProps(
      account = FullAccountMock,
      onConsolidationSuccess = { onConsolidationSuccessCalls.add(Unit) },
      onBack = {},
      context = UtxoConsolidationContext.PrivateWalletMigration
    )

    stateMachine.test(migrationProps) {
      // Loading
      awaitBody<LoadingSuccessBodyModel>()

      // First time: exceeds max count screen
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Confirmation screen (emits twice due to currency conversion)
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        onContinue.invoke()
      }

      // Tap & Hold
      awaitSheet<TapAndHoldToConsolidateUtxosBodyModel> {
        onConsolidate()
      }

      // NFC signing
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        onSuccess(PsbtMock)
      }

      // Broadcasting
      awaitBody<LoadingSuccessBodyModel>()

      // Success screen - before tapping Done, change the fake to return NOT_ENOUGH_UTXOS
      awaitBody<UtxoConsolidationTransactionSentModel> {
        utxoConsolidationService.prepareUtxoConsolidationResult =
          Err(NotEnoughUtxosToConsolidateError(utxoCount = 1))
        onDone()
      }

      // Auto-loop fires, shows loading while preparing next consolidation
      awaitBody<LoadingSuccessBodyModel>()

      // Prep fails with NotEnoughUtxosToConsolidateError, but since
      // preparationCount > 0, it calls onConsolidationSuccess instead
      // of showing an error screen.
      onConsolidationSuccessCalls.awaitItem()
    }
  }

  test("NFC signing back navigation returns to confirmation screen") {
    stateMachine.test(props) {
      // Loading the consolidation psbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Confirmation screen
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        onContinue.invoke()
      }

      // Tap & Hold info half sheet
      awaitSheet<TapAndHoldToConsolidateUtxosBodyModel> {
        onConsolidate()
      }

      // User cancels NFC signing
      awaitBodyMock<SignTransactionNfcSessionUiProps>("sign-txn-nfc") {
        psbt.shouldBe(PsbtMock)
        onBack()
      }

      // Should return to confirmation screen
      awaitBody<UtxoConsolidationConfirmationModel>()
      awaitBody<UtxoConsolidationConfirmationModel> {
        balanceTitle.shouldBe("Wallet balance")
      }
    }
  }
})
