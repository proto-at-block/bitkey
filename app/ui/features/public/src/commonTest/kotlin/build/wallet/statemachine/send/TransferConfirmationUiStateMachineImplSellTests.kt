package build.wallet.statemachine.send

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import bitkey.verification.TxVerificationServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.TxVerificationFeatureFlag
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.BitcoinMoney
import build.wallet.partnerships.PartnerInfoFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcContinuationSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcContinuationSessionUiStateMachine
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachineFake
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.icon.IconImage
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.immutableMapOf

class TransferConfirmationUiStateMachineImplSellTests : FunSpec({

  // Initialize turbine instances with unique names incorporating the testNameSuffix
  val onTransferInitiatedCalls = turbines.create<Psbt>("transferInitiatedCalls-sell")
  val onTransferFailedCalls = turbines.create<Unit>("transferFailedCalls-sell")
  val onBackCalls = turbines.create<Unit>("backCalls-sell")
  val onExitCalls = turbines.create<Unit>("exitCalls-sell")

  // Mock PSBTs with unique IDs
  val appSignedPsbt = PsbtMock.copy(id = "app-signed-psbt-sell")
  val appAndHwSignedPsbt = PsbtMock.copy(id = "app-and-hw-signed-psbt-sell")

  // Define the initial TransactionDetailsModel
  val initialModel = TransactionDetailsModel(
    transactionSpeedText = "~10 minutes",
    transactionDetailModelType = TransactionDetailModelType.Regular(
      transferAmountText = "$300.00",
      totalAmountPrimaryText = "$302.00",
      totalAmountSecondaryText = "546,347 sats",
      feeAmountText = "$2.00",
      feeAmountSecondaryText = "4,791 sats",
      transferAmountSecondaryText = "541,556 sats"
    )
  )

  // Initialize the TransactionDetailsCardUiStateMachine
  val transactionDetailsCardUiStateMachine = object : TransactionDetailsCardUiStateMachine,
    StateMachineMock<TransactionDetailsCardUiProps, TransactionDetailsModel>(
      initialModel = initialModel
    ) {}

  // Initialize the NfcSessionUIStateMachine
  val nfcSessionUIStateMachine = object : NfcContinuationSessionUiStateMachine,
    ScreenStateMachineMock<NfcContinuationSessionUIStateMachineProps<*>>("nfc-sell") {}

  // Define the TransferConfirmationUiProps with callbacks connected to the turbine instances
  @Suppress("DEPRECATION")
  val sellProps = TransferConfirmationUiProps(
    variant = TransferConfirmationScreenVariant.Sell(PartnerInfoFake),
    selectedPriority = FASTEST,
    recipientAddress = someBitcoinAddress,
    sendAmount = ExactAmount(BitcoinMoney.sats(123_456)),
    fees = immutableMapOf(
      FASTEST to Fee(BitcoinMoney.btc(10.0)),
      THIRTY_MINUTES to Fee(BitcoinMoney.btc(2.0)),
      SIXTY_MINUTES to Fee(BitcoinMoney.btc(1.0))
    ),
    exchangeRates = emptyImmutableList(),
    onTransferInitiated = { psbt, _ -> onTransferInitiatedCalls.add(psbt) },
    onTransferFailed = { onTransferFailedCalls.add(Unit) },
    onBack = { onBackCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  // Initialize shared services and dependencies
  val transactionPriorityPreference = TransactionPriorityPreferenceFake()
  val spendingWallet = SpendingWalletMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val feeOptionListUiStateMachine = FeeOptionListUiStateMachineFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val accountService = AccountServiceFake()
  val txVerificationService = TxVerificationServiceFake()
  val verificationFlag = TxVerificationFeatureFlag(FeatureFlagDaoFake())
  val accountConfigService = AccountConfigServiceFake()

  val stateMachine = TransferConfirmationUiStateMachineImpl(
    transactionDetailsCardUiStateMachine = transactionDetailsCardUiStateMachine,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    transactionPriorityPreference = transactionPriorityPreference,
    feeOptionListUiStateMachine = feeOptionListUiStateMachine,
    bitcoinWalletService = bitcoinWalletService,
    mobilePayService = mobilePayService,
    appFunctionalityService = appFunctionalityService,
    accountService = accountService,
    txVerificationService = txVerificationService,
    txVerificationFeatureFlag = verificationFlag,
    accountConfigService = accountConfigService
  )

  // Reset mocks before each test
  beforeTest {
    spendingWallet.reset()
    transactionPriorityPreference.reset()
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = spendingWallet
    mobilePayService.reset()
    accountService.reset()
    txVerificationService.reset()
    verificationFlag.reset()
  }

  // Invoke the shared test function, passing in all necessary parameters
  transferConfirmationUiStateMachineTests(
    props = sellProps,
    onTransferInitiatedCalls = onTransferInitiatedCalls,
    onBackCalls = onBackCalls,
    onExitCalls = onExitCalls,
    stateMachine = stateMachine,
    spendingWallet = spendingWallet,
    bitcoinWalletService = bitcoinWalletService,
    transactionPriorityPreference = transactionPriorityPreference,
    mobilePayService = mobilePayService,
    appSignedPsbt = appSignedPsbt,
    appAndHwSignedPsbt = appAndHwSignedPsbt,
    nfcSessionUIStateMachineId = nfcSessionUIStateMachine.id,
    txVerificationServiceFake = txVerificationService,
    verificationFlag = verificationFlag
  )

  test("[app & hw] successful signing syncs, broadcasts, calls onTransferInitiated") {
    val transactionPriority = FASTEST
    spendingWallet.createSignedPsbtResult = Ok(appSignedPsbt)

    stateMachine.test(
      sellProps.copy(
        selectedPriority = transactionPriority
      )
    ) {
      // CreatingAppSignedPsbt
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(sellProps.sendAmount)

      awaitBody<LoadingSuccessBodyModel>()

      // ViewingTransferConfirmation
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().iconModel.shouldNotBeNull().iconImage.shouldBe(
          IconImage.UrlImage(
            url = "test-partner-logo-url",
            fallbackIcon = Bitcoin
          )
        )
        header.shouldNotBeNull().headline.shouldBe("Confirm test-partner-name sale")

        mainContentList[0].shouldBeTypeOf<FormMainContentModel.Divider>()

        // Correct title, no fee selection enabled
        mainContentList[1]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .items[0].apply {
          title.shouldBe("Arrival time")
          sideText.shouldBe("~10 minutes")
          secondarySideText.shouldBeNull()
          onClick.shouldBeNull()
          onTitle.shouldBeNull()
        }

        // Only show transfer amount and fee.
        mainContentList[2]
          .shouldNotBeNull()
          .shouldBeTypeOf<DataList>()
          .apply {
            items[0].apply {
              title.shouldBe("Amount")
              onClick.shouldBeNull()
              onTitle.shouldBeNull()
              sideText.shouldBe("$300.00")
              secondarySideText.shouldBe("541,556 sats")
            }
            items[1].apply {
              title.shouldBe("Network fees")
              onClick.shouldBeNull()
              onTitle.shouldNotBeNull()
              titleIcon.shouldNotBeNull()
                .iconImage
                .shouldBe(IconImage.LocalImage(Icon.SmallIconInformationFilled))
              sideText.shouldBe("$2.00")
              secondarySideText.shouldBe("4,791 sats")
            }
          }

        clickPrimaryButton()
      }

      // SigningWithHardware
      awaitBodyMock<NfcContinuationSessionUIStateMachineProps<Psbt>>(
        id = nfcSessionUIStateMachine.id
      ) {
        shouldShowLongRunningOperation.shouldBeTrue()
        onSuccess(appAndHwSignedPsbt)
      }

      // FinalizingAndBroadcastingTransaction
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      bitcoinWalletService.broadcastedPsbts.test {
        awaitUntil { it.isNotEmpty() }.shouldContainExactly(appAndHwSignedPsbt)
      }
    }

    transactionPriorityPreference.preference.shouldBe(transactionPriority)
    onTransferInitiatedCalls.awaitItem()
  }
})
