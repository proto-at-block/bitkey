package build.wallet.statemachine.utxo

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId.UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.utxo.UtxoConsolidationParams
import build.wallet.bitcoin.utxo.UtxoConsolidationServiceFake
import build.wallet.bitcoin.utxo.UtxoConsolidationType
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UtxoConsolidationUiStateMachineImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val currencyConverter = CurrencyConverterFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val dateTimeFormatter = DateTimeFormatterMock()
  val timeZoneProvider = TimeZoneProviderMock()
  val utxoConsolidationService = UtxoConsolidationServiceFake()
  val nfcSessionUiStateMachine = object : NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      "nfc-fake-state-machine"
    ) {}

  val stateMachine = UtxoConsolidationUiStateMachineImpl(
    accountService = accountService,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    currencyConverter = currencyConverter,
    moneyDisplayFormatter = moneyDisplayFormatter,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider,
    utxoConsolidationService = utxoConsolidationService,
    nfcSessionUiStateMachine = nfcSessionUiStateMachine
  )

  val props = UtxoConsolidationProps(
    onConsolidationSuccess = {},
    onBack = {}
  )

  beforeTest {
    accountService.reset()
    utxoConsolidationService.reset()

    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
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
      awaitBodyMock<NfcSessionUIStateMachineProps<Psbt>> {
        shouldShowLongRunningOperation.shouldBeTrue()
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
})
