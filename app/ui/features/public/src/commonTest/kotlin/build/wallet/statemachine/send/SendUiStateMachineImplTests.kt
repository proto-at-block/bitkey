package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineMock
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetails
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration.Companion.minutes

class SendUiStateMachineImplTests : FunSpec({

  val permissionUiStateMachine = PermissionUiStateMachineMock()
  val clock = ClockFake()
  val rateSyncer = ExchangeRateServiceFake(clock = clock)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  fun sendUiStateMachine(
    usesDynamicIslandQrScannerPortal: Boolean = false,
    onDynamicIslandQrScanSuccess: () -> Unit = {},
    bitcoinQrCodeUiScanStateMachine: BitcoinQrCodeUiScanStateMachine =
      object : BitcoinQrCodeUiScanStateMachine, ScreenStateMachineMock<BitcoinQrCodeScanUiProps>(
        "bitcoin-qr-code"
      ) {},
  ) = SendUiStateMachineImpl(
    bitcoinAddressRecipientUiStateMachine =
      object : BitcoinAddressRecipientUiStateMachine,
        BodyStateMachineMock<BitcoinAddressRecipientUiProps>(
          "bitcoin-address-recipient"
        ) {},
    sendAmountEntryUiStateMachine =
      object : SendAmountEntryUiStateMachine,
        ScreenStateMachineMock<SendAmountEntryUiProps>(
          "send-amount-entry"
        ) {},
    transferConfirmationUiStateMachine =
      object : TransferConfirmationUiStateMachine,
        ScreenStateMachineMock<TransferConfirmationUiProps>(
          "transfer-confirmation"
        ) {},
    transferInitiatedUiStateMachine =
      object : TransferInitiatedUiStateMachine, BodyStateMachineMock<TransferInitiatedUiProps>(
        "transfer-initiated"
      ) {},
    bitcoinQrCodeUiScanStateMachine = bitcoinQrCodeUiScanStateMachine,
    permissionUiStateMachine = permissionUiStateMachine,
    feeSelectionUiStateMachine =
      object : FeeSelectionUiStateMachine, BodyStateMachineMock<FeeSelectionUiProps>(
        "fee-options"
      ) {},
    exchangeRateService = rateSyncer,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    usesDynamicIslandQrScannerPortalProvider = { usesDynamicIslandQrScannerPortal },
    onDynamicIslandQrScanSuccess = onDynamicIslandQrScanSuccess
  )

  val stateMachine = sendUiStateMachine()

  val bitcoinWalletService = BitcoinWalletServiceFake()

  val props = SendUiProps(
    account = FullAccountMock,
    validInvoiceInClipboard = null,
    onExit = {},
    onDone = {},
    onGoToUtxoConsolidation = {}
  )

  beforeTest {
    permissionUiStateMachine.isImplemented = true
    fiatCurrencyPreferenceRepository.reset()
    clock.reset()
    rateSyncer.reset()
    // Default the fallback sync to no-op so it doesn't interfere with test setups
    rateSyncer.syncRatesResult = Ok(emptyList())
    bitcoinWalletService.reset()

    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(1.0))
    )
  }

  val feeMap =
    persistentMapOf(
      FASTEST to Fee(BitcoinMoney.sats(1000)),
      THIRTY_MINUTES to Fee(BitcoinMoney.sats(300)),
      SIXTY_MINUTES to Fee(BitcoinMoney.sats(150))
    )
  context("User is sending exact amount") {
    val amountToSend = 60_000UL

    test("Golden path from send button") {
      stateMachine.test(props) {
        // Step 1: User enters some address
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitBodyMock<SendAmountEntryUiProps> {
          onContinueClick(ExactAmount(BitcoinMoney.sats(amountToSend.toBigInteger())))
        }

        // Step 3: User selects intended fee rate
        awaitBodyMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User views and broadcasts their transaction
        awaitBodyMock<TransferConfirmationUiProps> {
          val psbtToBroadcast =
            PsbtMock.copy(
              amountSats = amountToSend,
              fee = feeMap.getValue(FASTEST)
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 5: User is shown the "Transfer Initiated" screen
        awaitBodyMock<TransferInitiatedUiProps> {
          val transferAmount = BitcoinMoney.sats(amountToSend.toBigInteger())

          with(
            transactionDetails.shouldBeTypeOf<TransactionDetails.Regular>()
          ) {
            feeAmount.shouldBe(feeMap.getValue(FASTEST).amount)
            this.transferAmount.shouldBe(transferAmount)
          }
        }
      }
    }

    test("going back to amount entry initializes with btc currency") {
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitBodyMock<SendAmountEntryUiProps> {
          onContinueClick(ExactAmount(BitcoinMoney.zero()))
        }

        awaitBodyMock<FeeSelectionUiProps> {
          onBack()
        }

        awaitBodyMock<SendAmountEntryUiProps> {
          initialAmount.currency.shouldBe(BTC)
        }
      }
    }

    test("going back from transfer confirmation rehydrates the right data for previous steps") {
      stateMachine.test(props) {
        val moneyToSend = BitcoinMoney.sats(amountToSend.toBigInteger())
        // Step 1: User enters some address
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitBodyMock<SendAmountEntryUiProps> {
          onContinueClick(ExactAmount(moneyToSend))
        }

        // Step 3: User selects intended fee rate
        awaitBodyMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User reviews the transaction but hits "Back" button
        awaitBodyMock<TransferConfirmationUiProps> {
          onBack()
        }

        // Step 5: User is taken back to transfer amount input screen, with amount prefilled
        awaitBodyMock<SendAmountEntryUiProps> {
          initialAmount.shouldBe(moneyToSend)
          onBack()
        }

        // Step 6: User is taken back to address entry screen with the correct recipient address
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBe(someBitcoinAddress)
        }
      }
    }
  }

  context("User is sending all") {
    test("Golden path") {
      stateMachine.test(props) {
        // Step 1: User enters some address
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitBodyMock<SendAmountEntryUiProps> {
          onContinueClick(SendAll)
        }

        // Step 3: User selects intended fee rate
        awaitBodyMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User views and broadcasts their transaction. It is at this state machine where
        // BDK will assemble the "sweep" PSBT (`createAppSignedPsbt`)
        awaitBodyMock<TransferConfirmationUiProps> {
          val psbtToBroadcast =
            PsbtMock.copy(
              amountSats = 60_000UL,
              fee = feeMap.getValue(FASTEST)
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 5: User is shown the "Transfer Initiated" screen
        awaitBodyMock<TransferInitiatedUiProps> {
          val transferAmount = BitcoinMoney.sats(60_000UL.toBigInteger())

          with(
            transactionDetails.shouldBeTypeOf<TransactionDetails.Regular>()
          ) {
            feeAmount.shouldBe(feeMap.getValue(FASTEST).amount)
            this.transferAmount.shouldBe(transferAmount)
          }
        }
      }
    }

    test("going back from transfer confirmation rehydrates the right data for previous steps") {
      stateMachine.test(props) {
        // Step 1: User enters some address
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitBodyMock<SendAmountEntryUiProps> {
          onContinueClick(SendAll)
        }

        // Step 3: User selects intended fee rate
        awaitBodyMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User views and broadcasts their transaction. It is at this state machine where
        // BDK will assemble the "sweep" PSBT (`createAppSignedPsbt`)
        awaitBodyMock<TransferConfirmationUiProps> {
          onBack()
        }

        // Step 5: User is taken back to transfer amount input screen, with zero amount.
        awaitBodyMock<SendAmountEntryUiProps> {
          initialAmount.shouldBe(FiatMoney.zero(USD))
          onBack()
        }

        // Step 6: User is taken back to address entry screen with the correct recipient address.
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBe(someBitcoinAddress)
        }
      }
    }

    test("when exchange rates are stale, initial amount defaults to BTC and stale rates are filtered out") {
      val staleRates = listOf(
        ExchangeRate(
          IsoCurrencyTextCode("BTC"),
          IsoCurrencyTextCode("USD"),
          33333.0,
          clock.now - 7.minutes
        )
      )
      rateSyncer.exchangeRates.value = staleRates
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Stale rates fail the 6-minute freshness check and are filtered out
        awaitBodyMock<SendAmountEntryUiProps> {
          exchangeRates.shouldBeNull()
          initialAmount.currency.shouldBe(BTC)
        }
      }
    }

    test("going back to amount entry without exchange rates initializes with btc currency") {
      rateSyncer.exchangeRates.value = listOf()
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitBodyMock<SendAmountEntryUiProps> {
          onContinueClick(SendAll)
        }

        awaitBodyMock<FeeSelectionUiProps> {
          onBack()
        }

        awaitBodyMock<SendAmountEntryUiProps> {
          initialAmount.currency.shouldBe(BTC)
        }
      }
    }

    test("fallback sync populates rates when initially empty") {
      val freshRates = listOf(
        ExchangeRate(
          IsoCurrencyTextCode("BTC"),
          IsoCurrencyTextCode("USD"),
          50000.0,
          clock.now
        )
      )
      rateSyncer.exchangeRates.value = emptyList()
      // The fallback LaunchedEffect calls syncRates() when initialRates is null,
      // which populates the StateFlow and passes the freshness check
      rateSyncer.syncRatesResult = Ok(freshRates)
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Rates should appear via the fallback sync
        awaitBodyMock<SendAmountEntryUiProps> {
          exchangeRates.shouldBe(freshRates.toImmutableList())
          initialAmount.currency.shouldBe(BTC)
        }
      }
    }

    test("rates are locked after advancing past amount entry") {
      val originalRates = listOf(
        ExchangeRate(
          IsoCurrencyTextCode("BTC"),
          IsoCurrencyTextCode("USD"),
          50000.0,
          clock.now
        )
      )
      rateSyncer.exchangeRates.value = originalRates
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitBodyMock<SendAmountEntryUiProps> {
          exchangeRates.shouldBe(originalRates.toImmutableList())
          onContinueClick(ExactAmount(BitcoinMoney.sats(1000.toBigInteger())))
        }

        // Change rates while on fee selection
        val updatedRates = listOf(
          ExchangeRate(
            IsoCurrencyTextCode("BTC"),
            IsoCurrencyTextCode("USD"),
            99999.0,
            clock.now
          )
        )
        rateSyncer.exchangeRates.value = updatedRates

        // Fee selection should still see the original locked rates
        awaitBodyMock<FeeSelectionUiProps> {
          exchangeRates.shouldBe(originalRates.toImmutableList())
        }
      }
    }

    test("back from fee selection unlocks rates for dynamic population") {
      rateSyncer.exchangeRates.value = emptyList()
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // No rates yet — continue anyway
        awaitBodyMock<SendAmountEntryUiProps> {
          exchangeRates.shouldBeNull()
          onContinueClick(ExactAmount(BitcoinMoney.sats(1000.toBigInteger())))
        }

        // Back from fee selection unlocks rates
        awaitBodyMock<FeeSelectionUiProps> {
          onBack()
        }

        // Still no rates
        awaitBodyMock<SendAmountEntryUiProps> {
          exchangeRates.shouldBeNull()
        }

        // Fresh rates arrive (e.g., from foreground sync)
        val freshRates = listOf(
          ExchangeRate(
            IsoCurrencyTextCode("BTC"),
            IsoCurrencyTextCode("USD"),
            50000.0,
            clock.now
          )
        )
        rateSyncer.exchangeRates.value = freshRates

        // Rates should now appear because the lock was released on back
        awaitBodyMock<SendAmountEntryUiProps> {
          exchangeRates.shouldBe(freshRates.toImmutableList())
        }
      }
    }
  }

  context("QR scanner dismissal behavior") {
    test("closing QR scanner returns to address entry") {
      val stateMachineWithEmbeddedQr =
        sendUiStateMachine(
          bitcoinQrCodeUiScanStateMachine =
            object : BitcoinQrCodeUiScanStateMachine {
              @Composable
              override fun model(props: BitcoinQrCodeScanUiProps): ScreenModel {
                return BitcoinQrCodeScanBodyModel(
                  showSendToCopiedAddressButton = false,
                  showActionButtons = props.showActionButtons,
                  onQrCodeScanned = {},
                  onEnterAddressClick = props.onEnterAddressClick,
                  onClose = props.onClose,
                  onSendToCopiedAddressClick = {}
                ).asFullScreen()
              }
            }
        )

      stateMachineWithEmbeddedQr.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          showToolbarIcons.shouldBe(true)
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        awaitBody<SendRecipientAddressQrBodyModel> {
          scannerBodyModel.showActionButtons.shouldBe(false)
          recipientAddressBodyModel.shouldBeTypeOf<BodyModelMock<BitcoinAddressRecipientUiProps>>()
            .latestProps.showToolbarIcons.shouldBe(false)
          scannerBodyModel.onClose()
        }

        awaitBody<SendRecipientAddressQrBodyModel> {
          addressSheetExpanded.shouldBe(true)
          recipientAddressBodyModel.shouldBeTypeOf<BodyModelMock<BitcoinAddressRecipientUiProps>>()
            .latestProps.showToolbarIcons.shouldBe(true)
          onAddressSheetRestored()
        }

        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBeNull()
          showToolbarIcons.shouldBe(true)
        }
      }
    }

    test("dismissing self-send QR error returns to address entry") {
      val stateMachineWithSelfSendErrorQr =
        sendUiStateMachine(
          bitcoinQrCodeUiScanStateMachine =
            object : BitcoinQrCodeUiScanStateMachine {
              @Composable
              override fun model(props: BitcoinQrCodeScanUiProps): ScreenModel {
                var showingSelfSendError by remember { mutableStateOf(false) }

                return if (showingSelfSendError) {
                  ErrorFormBodyModel(
                    title = "This is your Bitkey wallet address",
                    primaryButton = ButtonDataModel(
                      text = "Done",
                      onClick = props.onClose
                    ),
                    eventTrackerScreenId = null
                  ).asModalScreen()
                } else {
                  BitcoinQrCodeScanBodyModel(
                    showSendToCopiedAddressButton = false,
                    showActionButtons = props.showActionButtons,
                    onQrCodeScanned = { showingSelfSendError = true },
                    onEnterAddressClick = props.onEnterAddressClick,
                    onClose = props.onClose,
                    onSendToCopiedAddressClick = {}
                  ).asFullScreen()
                }
              }
            }
        )

      stateMachineWithSelfSendErrorQr.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        awaitBody<SendRecipientAddressQrBodyModel> {
          scannerBodyModel.onQrCodeScanned("self-send-address")
        }

        awaitBody<FormBodyModel> {
          primaryButton.shouldNotBeNull().onClick()
        }

        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBeNull()
          showToolbarIcons.shouldBe(true)
        }
      }
    }

    test("dismissing camera permission returns to address entry") {
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onExit()
        }

        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBeNull()
        }
      }
    }

    test("dynamic island QR scanner requests camera permission before opening portal") {
      sendUiStateMachine(usesDynamicIslandQrScannerPortal = true).test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        awaitBody<DynamicIslandQrScannerPortalBodyModel> {
          isClosing.shouldBe(false)
          qrScannerScreenModel
            .shouldNotBeNull()
            .body
            .shouldBeTypeOf<BodyModelMock<BitcoinQrCodeScanUiProps>>()
            .latestProps
            .showActionButtons
            .shouldBe(true)
        }
      }
    }

    test("dynamic island QR scanner haptic runs after validated recipient scan") {
      var hapticCalls = 0

      sendUiStateMachine(
        usesDynamicIslandQrScannerPortal = true,
        onDynamicIslandQrScanSuccess = { hapticCalls += 1 }
      ).test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        awaitBody<DynamicIslandQrScannerPortalBodyModel> {
          hapticCalls.shouldBe(0)
          qrScannerScreenModel
            .shouldNotBeNull()
            .body
            .shouldBeTypeOf<BodyModelMock<BitcoinQrCodeScanUiProps>>()
            .latestProps
            .onRecipientScanned(someBitcoinAddress)
        }

        hapticCalls.shouldBe(1)
        awaitBodyMock<SendAmountEntryUiProps> {
          recipientAddress.shouldBe(someBitcoinAddress)
        }
      }
    }

    test("dynamic island QR scanner unmounts scanner while closing portal") {
      sendUiStateMachine(usesDynamicIslandQrScannerPortal = true).test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        awaitBody<DynamicIslandQrScannerPortalBodyModel> {
          qrScannerScreenModel.shouldNotBeNull()
          onClose()
        }

        awaitBody<DynamicIslandQrScannerPortalBodyModel> {
          isClosing.shouldBe(true)
          qrScannerScreenModel.shouldBeNull()
          onClosed()
        }

        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBeNull()
        }
      }
    }

    test("dynamic island QR scanner ignores queued scan result after close starts") {
      var hapticCalls = 0

      sendUiStateMachine(
        usesDynamicIslandQrScannerPortal = true,
        onDynamicIslandQrScanSuccess = { hapticCalls += 1 }
      ).test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        lateinit var scannerProps: BitcoinQrCodeScanUiProps
        awaitBody<DynamicIslandQrScannerPortalBodyModel> {
          scannerProps =
            qrScannerScreenModel
              .shouldNotBeNull()
              .body
              .shouldBeTypeOf<BodyModelMock<BitcoinQrCodeScanUiProps>>()
              .latestProps
          onClose()
        }

        scannerProps.onRecipientScanned(someBitcoinAddress)
        hapticCalls.shouldBe(0)

        awaitBody<DynamicIslandQrScannerPortalBodyModel> {
          isClosing.shouldBe(true)
          onClosed()
        }

        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBeNull()
        }
      }
    }
  }
})
