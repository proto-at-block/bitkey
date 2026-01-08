package build.wallet.statemachine.send

import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineMock
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetails
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.time.ClockFake
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.time.Duration.Companion.minutes

class SendUiStateMachineImplTests : FunSpec({

  val permissionUiStateMachine = PermissionUiStateMachineMock()
  val clock = ClockFake()
  val rateSyncer = ExchangeRateServiceFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val stateMachine =
    SendUiStateMachineImpl(
      bitcoinAddressRecipientUiStateMachine =
        object : BitcoinAddressRecipientUiStateMachine,
          BodyStateMachineMock<BitcoinAddressRecipientUiProps>(
            "bitcoin-address-recipient"
          ) {},
      transferAmountEntryUiStateMachine =
        object : TransferAmountEntryUiStateMachine,
          ScreenStateMachineMock<TransferAmountEntryUiProps>(
            "transfer-amount-entry"
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
      bitcoinQrCodeUiScanStateMachine =
        object : BitcoinQrCodeUiScanStateMachine, ScreenStateMachineMock<BitcoinQrCodeScanUiProps>(
          "bitcoin-qr-code"
        ) {},
      permissionUiStateMachine = permissionUiStateMachine,
      feeSelectionUiStateMachine =
        object : FeeSelectionUiStateMachine, BodyStateMachineMock<FeeSelectionUiProps>(
          "fee-options"
        ) {},
      exchangeRateService = rateSyncer,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
    )

  val bitcoinWalletService = BitcoinWalletServiceFake()

  val props = SendUiProps(
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
        awaitBodyMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              sendAmount = ExactAmount(BitcoinMoney.sats(amountToSend.toBigInteger()))
            )
          )
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
              fee = feeMap[FASTEST]!!
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 5: User is shown the "Transfer Initiated" screen
        awaitBodyMock<TransferInitiatedUiProps> {
          val transferAmount = BitcoinMoney.sats(amountToSend.toBigInteger())

          with(
            transactionDetails.shouldBeTypeOf<TransactionDetails.Regular>()
          ) {
            feeAmount.shouldBe(feeMap[FASTEST]!!.amount)
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

        awaitBodyMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              ExactAmount(BitcoinMoney.zero())
            )
          )
        }

        awaitBodyMock<FeeSelectionUiProps> {
          onBack()
        }

        awaitBodyMock<TransferAmountEntryUiProps> {
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
        awaitBodyMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              ExactAmount(moneyToSend)
            )
          )
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
        awaitBodyMock<TransferAmountEntryUiProps> {
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
        awaitBodyMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              SendAll
            )
          )
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
              fee = feeMap[FASTEST]!!
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 5: User is shown the "Transfer Initiated" screen
        awaitBodyMock<TransferInitiatedUiProps> {
          val transferAmount = BitcoinMoney.sats(60_000UL.toBigInteger())

          with(
            transactionDetails.shouldBeTypeOf<TransactionDetails.Regular>()
          ) {
            feeAmount.shouldBe(feeMap[FASTEST]!!.amount)
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
        awaitBodyMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              SendAll
            )
          )
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
        awaitBodyMock<TransferAmountEntryUiProps> {
          initialAmount.shouldBe(FiatMoney.zero(USD))
          onBack()
        }

        // Step 6: User is taken back to address entry screen with the correct recipient address.
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBe(someBitcoinAddress)
        }
      }
    }

    test("when exchange rates are 5 minutes out of date, exchange rates are null") {
      rateSyncer.exchangeRates.value =
        listOf(
          ExchangeRate(
            IsoCurrencyTextCode("BTC"),
            IsoCurrencyTextCode("USD"),
            33333.0,
            clock.now - 5.minutes
          )
        )
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitBodyMock<TransferAmountEntryUiProps> {
          exchangeRates.shouldBeNull()
        }
      }
    }

    test("going back to amount entry without exchange rates initializes with btc currency") {
      rateSyncer.exchangeRates.value = listOf()
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitBodyMock<TransferAmountEntryUiProps> {
          onContinueClick(ContinueTransferParams(SendAll))
        }

        awaitBodyMock<FeeSelectionUiProps> {
          onBack()
        }

        awaitBodyMock<TransferAmountEntryUiProps> {
          initialAmount.currency.shouldBe(BTC)
        }
      }
    }
  }

  context("QR scanner dismissal behavior") {
    test("closing QR scanner returns to address entry") {
      stateMachine.test(props) {
        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          onScanQrCodeClick()
        }

        awaitBodyMock<PermissionUiProps> {
          onGranted()
        }

        awaitBodyMock<BitcoinQrCodeScanUiProps> {
          onClose()
        }

        awaitBodyMock<BitcoinAddressRecipientUiProps> {
          address.shouldBeNull()
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
  }
})
