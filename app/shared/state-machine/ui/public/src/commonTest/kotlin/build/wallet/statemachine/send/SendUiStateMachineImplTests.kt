package build.wallet.statemachine.send

import build.wallet.availability.NetworkReachabilityProviderMock
import build.wallet.bitcoin.address.bitcoinAddressP2TR
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.fees.oneSatPerVbyteFeeRate
import build.wallet.bitcoin.transactions.*
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitkey.factor.SigningFactor
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
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineMock
import build.wallet.statemachine.send.SendEntryPoint.SendButton
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachine
import build.wallet.time.ClockFake
import com.ionspin.kotlin.bignum.decimal.BigDecimal
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
  val networkReachabilityProvider = NetworkReachabilityProviderMock(turbines::create)
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
      clock = clock,
      networkReachabilityProvider = networkReachabilityProvider,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
    )

  val transactionsService = TransactionsServiceFake()

  val props =
    SendUiProps(
      entryPoint = SendButton,
      account = FullAccountMock,
      validInvoiceInClipboard = null,
      onExit = {},
      onDone = {}
    )

  beforeTest {
    permissionUiStateMachine.isImplemented = true
    fiatCurrencyPreferenceRepository.reset()
    clock.reset()
    rateSyncer.reset()
    transactionsService.reset()

    transactionsService.transactionsData.value = KeyboxTransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(1.0))
    )
  }

  val feeMap =
    persistentMapOf(
      FASTEST to Fee(BitcoinMoney.sats(1000), oneSatPerVbyteFeeRate),
      THIRTY_MINUTES to Fee(BitcoinMoney.sats(300), oneSatPerVbyteFeeRate),
      SIXTY_MINUTES to Fee(BitcoinMoney.sats(150), oneSatPerVbyteFeeRate)
    )
  context("User is sending exact amount") {
    val amountToSend = 60_000UL

    test("Golden path from send button") {
      stateMachine.test(props) {
        // Step 1: User enters some address
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              sendAmount = ExactAmount(BitcoinMoney.sats(amountToSend.toBigInteger())),
              fiatMoney = FiatMoney.usd(dollars = BigDecimal.TEN),
              requiredSigner = SigningFactor.Hardware,
              spendingLimit = null
            )
          )
        }

        // Step 3: User selects intended fee rate
        awaitScreenWithBodyModelMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User views and broadcasts their transaction
        awaitScreenWithBodyModelMock<TransferConfirmationUiProps> {
          val psbtToBroadcast =
            PsbtMock.copy(
              amountSats = amountToSend,
              fee = feeMap[FASTEST]!!.amount
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 5: User is shown the "Transfer Initiated" screen
        awaitScreenWithBodyModelMock<TransferInitiatedUiProps> {
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
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              ExactAmount(BitcoinMoney.zero()),
              FiatMoney.zeroUsd(),
              SigningFactor.Hardware,
              null
            )
          )
        }

        awaitScreenWithBodyModelMock<FeeSelectionUiProps> {
          onBack()
        }

        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          initialAmount.currency.shouldBe(BTC)
        }
      }
    }

    test("going back from transfer confirmation rehydrates the right data for previous steps") {
      stateMachine.test(props) {
        val moneyToSend = BitcoinMoney.sats(amountToSend.toBigInteger())
        // Step 1: User enters some address
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              ExactAmount(moneyToSend),
              FiatMoney.usd(dollars = BigDecimal.TEN),
              SigningFactor.Hardware,
              null
            )
          )
        }

        // Step 3: User selects intended fee rate
        awaitScreenWithBodyModelMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User reviews the transaction but hits "Back" button
        awaitScreenWithBodyModelMock<TransferConfirmationUiProps> {
          onBack()
        }

        // Step 5: User is taken back to transfer amount input screen, with amount prefilled
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          initialAmount.shouldBe(moneyToSend)
          onBack()
        }

        // Step 6: User is taken back to address entry screen with the correct recipient address
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          address.shouldBe(someBitcoinAddress)
        }
      }
    }
  }

  context("User is sending all") {
    test("Golden path") {
      stateMachine.test(props) {
        // Step 1: User enters some address
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              SendAll,
              FiatMoney.usd(dollars = BigDecimal.TEN),
              SigningFactor.Hardware,
              null
            )
          )
        }

        // Step 3: User selects intended fee rate
        awaitScreenWithBodyModelMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User views and broadcasts their transaction. It is at this state machine where
        // BDK will assemble the "sweep" PSBT (`createAppSignedPsbt`)
        awaitScreenWithBodyModelMock<TransferConfirmationUiProps> {
          val psbtToBroadcast =
            PsbtMock.copy(
              amountSats = 60_000UL,
              fee = feeMap[FASTEST]!!.amount
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 5: User is shown the "Transfer Initiated" screen
        awaitScreenWithBodyModelMock<TransferInitiatedUiProps> {
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
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              SendAll,
              FiatMoney.usd(dollars = BigDecimal.TEN),
              SigningFactor.Hardware,
              null
            )
          )
        }

        // Step 3: User selects intended fee rate
        awaitScreenWithBodyModelMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: User views and broadcasts their transaction. It is at this state machine where
        // BDK will assemble the "sweep" PSBT (`createAppSignedPsbt`)
        awaitScreenWithBodyModelMock<TransferConfirmationUiProps> {
          onBack()
        }

        // Step 5: User is taken back to transfer amount input screen, with zero amount.
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          initialAmount.shouldBe(FiatMoney.zero(USD))
          onBack()
        }

        // Step 6: User is taken back to address entry screen with the correct recipient address.
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
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
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          exchangeRates.shouldBeNull()
        }
      }
    }

    test("given f8e network is not reachable, the required signed is HW") {
      stateMachine.test(props) {

        // Step 1: User enters some address
        awaitScreenWithBodyModelMock<BitcoinAddressRecipientUiProps> {
          onRecipientEntered(someBitcoinAddress)
        }

        // Step 2: User enters some amount they want to send
        awaitScreenWithBodyModelMock<TransferAmountEntryUiProps> {
          onContinueClick(
            ContinueTransferParams(
              SendAll,
              FiatMoney.usd(dollars = BigDecimal.TEN),
              SigningFactor.Hardware,
              null
            )
          )
        }

        // Step 3: User selects intended fee rate
        awaitScreenWithBodyModelMock<FeeSelectionUiProps> {
          onContinue(FASTEST, feeMap)
        }

        // Step 4: Should have HW required signer
        awaitScreenWithBodyModelMock<TransferConfirmationUiProps> {
          requiredSigner.shouldBe(SigningFactor.Hardware)
        }
      }
    }
  }

  context("Entering from speed up") {
    val speedUpEntryProps =
      SendUiProps(
        entryPoint =
          SendEntryPoint.SpeedUp(
            speedUpTransactionDetails =
              SpeedUpTransactionDetails(
                txid = "",
                recipientAddress = bitcoinAddressP2TR,
                sendAmount = BitcoinMoney.sats(50_000),
                oldFee = Fee(BitcoinMoney.sats(125), feeRate = FeeRate(satsPerVByte = 1f))
              ),
            fiatMoney = FiatMoney.usd(dollars = 5.0),
            spendingLimit = null,
            newFeeRate = FeeRate(satsPerVByte = 2f),
            fees = persistentMapOf()
          ),
        account = FullAccountMock,
        validInvoiceInClipboard = null,
        onExit = {},
        onDone = {}
      )

    test("Goes straight to transfer confirmation screen") {
      stateMachine.test(speedUpEntryProps) {
        val amountToSend = 50_000UL
        // Step 1: User is shown transaction confirmation screen
        awaitScreenWithBodyModelMock<TransferConfirmationUiProps> {
          transferVariant.shouldBeTypeOf<TransferConfirmationUiProps.Variant.SpeedUp>()

          val psbtToBroadcast =
            PsbtMock.copy(
              amountSats = amountToSend,
              fee = feeMap[FASTEST]!!.amount
            )
          onTransferInitiated(psbtToBroadcast, FASTEST)
        }

        // Step 2: User is shown the "Transfer Initiated" screen
        awaitScreenWithBodyModelMock<TransferInitiatedUiProps> {
          val intendedFee = feeMap[FASTEST]
          val transferAmount = BitcoinMoney.sats(amountToSend.toBigInteger())

          with(
            transactionDetails.shouldBeTypeOf<TransactionDetails.SpeedUp>()
          ) {
            this.transferAmount.shouldBe(transferAmount)
            feeAmount.shouldBe(intendedFee!!.amount)
          }
        }
      }
    }
  }
})
