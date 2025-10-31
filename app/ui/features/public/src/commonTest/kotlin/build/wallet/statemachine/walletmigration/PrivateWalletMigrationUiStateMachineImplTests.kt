package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action.ACTION_APP_PRIVATE_WALLET_MANUAL_UPGRADE
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsData
import build.wallet.bitcoin.utxo.UtxoConsolidationContext.PrivateWalletMigration
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SekGeneratorMock
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.NetworkFeesInfoSheetModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.wallet.migration.PrivateWalletMigrationError.KeysetCreationFailed
import build.wallet.wallet.migration.PrivateWalletMigrationError.MigrationCompletionFailed
import build.wallet.wallet.migration.PrivateWalletMigrationServiceFake
import build.wallet.wallet.migration.PrivateWalletMigrationState.CloudBackupCompleted
import build.wallet.wallet.migration.PrivateWalletMigrationState.InKeysetCreation.HwKeyCreated
import build.wallet.wallet.migration.PrivateWalletMigrationState.ServerKeysetActivated
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.persistentListOf

class PrivateWalletMigrationUiStateMachineImplTests : FunSpec({
  val refreshAuthTokensUiStateMachine =
    object : RefreshAuthTokensUiStateMachine,
      ScreenStateMachineMock<RefreshAuthTokensProps>("refresh-auth-tokens") {}

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}

  val sweepUiStateMachine =
    object : SweepUiStateMachine,
      ScreenStateMachineMock<SweepUiProps>("sweep") {}

  val utxoConsolidationUiStateMachine =
    object : UtxoConsolidationUiStateMachine,
      ScreenStateMachineMock<UtxoConsolidationProps>("utxo-consolidation") {}

  val fullAccountCloudSignInAndBackupUiStateMachine =
    object : FullAccountCloudSignInAndBackupUiStateMachine,
      ScreenStateMachineMock<FullAccountCloudSignInAndBackupProps>("full-account-cloud-sign-in-and-backup") {}

  val uuidGenerator = UuidGeneratorFake()

  val privateWalletMigrationService = PrivateWalletMigrationServiceFake().apply {
    estimateMigrationFeesResult = Ok(BitcoinMoney.sats(1000))
  }

  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val eventTracker = EventTrackerMock(turbines::create)

  val bitcoinWalletService = BitcoinWalletServiceFake().apply {
    transactionsData.value = TransactionsData(
      balance = BitcoinBalanceFake,
      fiatBalance = null,
      transactions = persistentListOf(),
      utxos = Utxos(confirmed = emptySet(), unconfirmed = emptySet())
    )
  }

  val utxoMaxConsolidationCountFeatureFlag = UtxoMaxConsolidationCountFeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )

  val moneyAmountUiStateMachine =
    object : MoneyAmountUiStateMachine {
      @androidx.compose.runtime.Composable
      override fun model(props: MoneyAmountUiProps): MoneyAmountModel {
        return MoneyAmountModel(
          primaryAmount = "1,000 sats",
          secondaryAmount = "$1.00"
        )
      }
    }

  val sekGenerator = SekGeneratorMock()

  val stateMachine = PrivateWalletMigrationUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    refreshAuthTokensUiStateMachine = refreshAuthTokensUiStateMachine,
    sweepUiStateMachine = sweepUiStateMachine,
    utxoConsolidationUiStateMachine = utxoConsolidationUiStateMachine,
    uuidGenerator = uuidGenerator,
    privateWalletMigrationService = privateWalletMigrationService,
    moneyAmountUiStateMachine = moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    inAppBrowserNavigator = inAppBrowserNavigator,
    eventTracker = eventTracker,
    sekGenerator = sekGenerator,
    bitcoinWalletService = bitcoinWalletService,
    utxoMaxConsolidationCountFeatureFlag = utxoMaxConsolidationCountFeatureFlag,
    fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine
  )

  val onMigrationCompleteCalls = turbines.create<FullAccount>("onMigrationComplete calls")
  val onExitCalls = turbines.create<Unit>("onExit calls")

  val props = PrivateWalletMigrationUiProps(
    account = FullAccountMock,
    onMigrationComplete = { onMigrationCompleteCalls.add(it) },
    onExit = { onExitCalls.add(Unit) }
  )

  /**
   * Helper function to generate confirmed UTXOs for testing
   */
  fun generateConfirmedUtxos(count: Int): Set<BdkUtxo> {
    return (1..count).map { index ->
      BdkUtxo(
        outPoint = BdkOutPoint(
          txid = "utxo-txid-$index",
          vout = index.toUInt()
        ),
        txOut = BdkTxOut(
          value = 10000u,
          scriptPubkey = BdkScriptMock()
        ),
        isSpent = false
      )
    }.toSet()
  }

  /**
   * Helper function to set up transaction data with specified UTXOs
   */
  fun setTransactionData(
    confirmed: Set<BdkUtxo>,
    unconfirmed: Set<BdkUtxo>,
  ) {
    bitcoinWalletService.transactionsData.value = TransactionsData(
      balance = BitcoinBalanceFake,
      fiatBalance = null,
      transactions = persistentListOf(),
      utxos = Utxos(confirmed = confirmed, unconfirmed = unconfirmed)
    )
  }

  beforeTest {
    privateWalletMigrationService.reset()
    utxoMaxConsolidationCountFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(150.0))
  }

  test("successful migration flow") {
    // Return an updated keybox with the new private keyset as active
    val updatedKeybox = FullAccountMock.keybox.copy(
      activeSpendingKeyset = PrivateSpendingKeysetMock,
      keysets = listOf(PrivateSpendingKeysetMock, SpendingKeysetMock)
    )
    privateWalletMigrationService.initiateMigrationResult = Ok(
      ServerKeysetActivated(
        updatedKeybox = updatedKeybox,
        newKeyset = PrivateSpendingKeysetMock,
        sealedCsek = null
      )
    )

    stateMachine.test(props) {
      val mockProofOfPossession = HwFactorProofOfPossession("proof")
      val mockHwKeys = HwKeyBundle(
        localId = "test-uuid",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
        networkType = FullAccountMock.keybox.config.bitcoinNetworkType
      )
      val sessionResult = KeysetInitiationNfcResult(
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockHwKeys,
        sealedSsek = SealedSsekFake,
        ssek = SsekFake
      )

      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
            estimatedFee.shouldNotBeNull()
            estimatedFeeSats.shouldNotBeNull()
          }
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        fullAccountId.shouldBe(FullAccountMock.accountId)
        appAuthKey.shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<FullAccountCloudSignInAndBackupProps>(id = "full-account-cloud-sign-in-and-backup") {
        keybox.shouldBe(updatedKeybox)
        onBackupSaved()
      }

      awaitBodyMock<SweepUiProps>(id = "sweep") {
        keybox.shouldBe(updatedKeybox)
        onSuccess()
      }

      awaitBody<PrivateWalletMigrationCompleteBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Got it")
          onClick()
        }
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(ACTION_APP_PRIVATE_WALLET_MANUAL_UPGRADE)
      onMigrationCompleteCalls.awaitItem().shouldBe(FullAccountMock)
    }
  }

  test("skips cloud backup step when CloudBackupCompleted is returned") {
    // Return CloudBackupCompleted instead of ServerKeysetActivated
    val updatedKeybox = FullAccountMock.keybox.copy(
      activeSpendingKeyset = PrivateSpendingKeysetMock,
      keysets = listOf(PrivateSpendingKeysetMock, SpendingKeysetMock)
    )
    privateWalletMigrationService.initiateMigrationResult = Ok(
      CloudBackupCompleted(
        updatedKeybox = updatedKeybox,
        newKeyset = PrivateSpendingKeysetMock
      )
    )

    stateMachine.test(props) {
      val mockProofOfPossession = HwFactorProofOfPossession("proof")
      val mockHwKeys = HwKeyBundle(
        localId = "test-uuid",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
        networkType = FullAccountMock.keybox.config.bitcoinNetworkType
      )
      val sessionResult = KeysetInitiationNfcResult(
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockHwKeys,
        sealedSsek = SealedSsekFake,
        ssek = SsekFake
      )

      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
            estimatedFee.shouldNotBeNull()
            estimatedFeeSats.shouldNotBeNull()
          }
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        fullAccountId.shouldBe(FullAccountMock.accountId)
        appAuthKey.shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Should skip cloud backup and go directly to sweep
      awaitBodyMock<SweepUiProps>(id = "sweep") {
        keybox.shouldBe(updatedKeybox)
        onSuccess()
      }

      awaitBody<PrivateWalletMigrationCompleteBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Got it")
          onClick()
        }
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(ACTION_APP_PRIVATE_WALLET_MANUAL_UPGRADE)
      onMigrationCompleteCalls.awaitItem().shouldBe(FullAccountMock)
    }
  }

  test("shows insufficient funds message when balance is too low") {
    privateWalletMigrationService.estimateMigrationFeesResult =
      Err(build.wallet.wallet.migration.PrivateWalletMigrationError.InsufficientFundsForMigration)

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.InsufficientFunds>()
          val sublineText =
            (header?.sublineModel as? build.wallet.statemachine.core.LabelModel.StringModel)?.string
          sublineText.shouldBe("Your balance is less than the current network fees so will not be transferred to your new wallet.")
          mainContentList.shouldBeEmpty()
          primaryButton.shouldNotBeNull()
        }
    }
  }

  test("Back exits flow") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }

      onExitCalls.awaitItem()
    }
  }

  test("back during auth token refresh returns to intro") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
            estimatedFee.shouldNotBeNull()
            estimatedFeeSats.shouldNotBeNull()
          }
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        onBack()
      }

      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("cancel during hardware initiation returns to intro") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
            estimatedFee.shouldNotBeNull()
            estimatedFeeSats.shouldNotBeNull()
          }
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onCancel()
      }

      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("learn how opens in-app browser with privacy URL") {
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onLearnHow()
      }

      awaitBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://bitkey.world/hc/enhanced-wallet-privacy")
      inAppBrowserNavigator.onCloseCallback?.invoke()

      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("shows network fees info sheet when explainer is clicked") {
    privateWalletMigrationService.estimateMigrationFeesResult = Ok(BitcoinMoney.sats(1000))

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      // Loading state
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
        }

      // Fee estimate loaded
      val feeEstimateSheet = awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>()

      feeEstimateSheet.feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
        estimatedFee.shouldNotBeNull()
        estimatedFeeSats.shouldNotBeNull()
        // Click the network fees explainer
        onNetworkFeesExplainerClick()
      }

      // Network fees info sheet should be shown
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<NetworkFeesInfoSheetModel>().apply {
          header.shouldNotBeNull().headline.shouldBe("Network fees")
          primaryButton.shouldNotBeNull().text.shouldBe("Got it")
          onBack()
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>()
        }
    }
  }

  test("Resuming migration skips fee estimate") {
    val propsWithInProgress = props.copy(inProgress = true)

    stateMachine.test(propsWithInProgress) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onBack.shouldBe(null)
        onContinue()
      }

      awaitBodyMock<RefreshAuthTokensProps> {
        fullAccountId.shouldBe(FullAccountMock.accountId)
        appAuthKey.shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
      }
    }
  }

  test("completeMigration is called after successful sweep") {
    val updatedKeybox = FullAccountMock.keybox.copy(
      activeSpendingKeyset = PrivateSpendingKeysetMock,
      keysets = listOf(PrivateSpendingKeysetMock, SpendingKeysetMock)
    )
    privateWalletMigrationService.initiateMigrationResult = Ok(
      ServerKeysetActivated(
        updatedKeybox = updatedKeybox,
        newKeyset = PrivateSpendingKeysetMock,
        sealedCsek = null
      )
    )

    stateMachine.test(props) {
      val mockProofOfPossession = HwFactorProofOfPossession("proof")
      val mockHwKeys = HwKeyBundle(
        localId = "test-uuid",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
        networkType = FullAccountMock.keybox.config.bitcoinNetworkType
      )
      val sessionResult = KeysetInitiationNfcResult(
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockHwKeys,
        sealedSsek = SealedSsekFake,
        ssek = SsekFake
      )

      // Navigate through the flow to the sweep state
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
            estimatedFee.shouldNotBeNull()
            estimatedFeeSats.shouldNotBeNull()
          }
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBodyMock<FullAccountCloudSignInAndBackupProps>(id = "full-account-cloud-sign-in-and-backup") {
        keybox.shouldBe(updatedKeybox)
        onBackupSaved()
      }

      awaitBodyMock<SweepUiProps>(id = "sweep") {
        keybox.shouldBe(updatedKeybox)
        // Call onSuccess to trigger completeMigration
        onSuccess()
      }
      awaitBody<PrivateWalletMigrationCompleteBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Got it")
          onClick()
        }
      }
      // Verify event was tracked after finishing
      eventTracker.eventCalls.awaitItem().action.shouldBe(ACTION_APP_PRIVATE_WALLET_MANUAL_UPGRADE)
      onMigrationCompleteCalls.awaitItem().shouldBe(FullAccountMock)
    }

    // Verify completeMigration was called exactly once
    privateWalletMigrationService.completeMigrationCallCount.shouldBe(1)
  }

  test("shows error screen when completeMigration fails") {
    // Set up failure result
    privateWalletMigrationService.completeMigrationResult = Err(
      MigrationCompletionFailed(RuntimeException("Failed to complete migration"))
    )

    val updatedKeybox = FullAccountMock.keybox.copy(
      activeSpendingKeyset = PrivateSpendingKeysetMock,
      keysets = listOf(PrivateSpendingKeysetMock, SpendingKeysetMock)
    )
    privateWalletMigrationService.initiateMigrationResult = Ok(
      ServerKeysetActivated(
        updatedKeybox = updatedKeybox,
        newKeyset = PrivateSpendingKeysetMock,
        sealedCsek = null
      )
    )

    stateMachine.test(props) {
      val mockProofOfPossession = HwFactorProofOfPossession("proof")
      val mockHwKeys = HwKeyBundle(
        localId = "test-uuid",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
        networkType = FullAccountMock.keybox.config.bitcoinNetworkType
      )
      val sessionResult = KeysetInitiationNfcResult(
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockHwKeys,
        sealedSsek = SealedSsekFake,
        ssek = SsekFake
      )

      // Navigate through the flow to the sweep state
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
          primaryButton.shouldBe(null)
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>().apply {
            estimatedFee.shouldNotBeNull()
            estimatedFeeSats.shouldNotBeNull()
          }
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBodyMock<FullAccountCloudSignInAndBackupProps>(id = "full-account-cloud-sign-in-and-backup") {
        keybox.shouldBe(updatedKeybox)
        onBackupSaved()
      }

      awaitBodyMock<SweepUiProps>(id = "sweep") {
        keybox.shouldBe(updatedKeybox)
        // Call onSuccess to trigger completeMigration
        onSuccess()
      }

      awaitBody<PrivateWalletMigrationCompleteBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Got it")
          onClick()
        }
      }

      // Verify we show the error screen instead of success
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Migration Error")
      }
    }
  }
  test("error screen shows cancel button when migration not in progress") {
    privateWalletMigrationService.initiateMigrationResult =
      Err(KeysetCreationFailed(RuntimeException("test error")))

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
        }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loaded>()
          primaryButton.shouldNotBeNull()
          onConfirm()
        }

      awaitBodyMock<RefreshAuthTokensProps> {
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        val sessionResult = KeysetInitiationNfcResult(
          proofOfPossession = HwFactorProofOfPossession("proof"),
          newHwKeys = HwKeyBundle(
            localId = "test-uuid",
            spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
            authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
            networkType = FullAccountMock.keybox.config.bitcoinNetworkType
          ),
          sealedSsek = SealedSsekFake,
          ssek = SsekFake
        )
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Migration Error")
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Cancel")
          onClick()
        }
      }

      onExitCalls.awaitItem()
    }
  }

  test("error screen hides cancel button when migration in progress") {
    privateWalletMigrationService.migrationState.value = HwKeyCreated(
      newHwKeys = HwSpendingPublicKeyMock
    )
    val propsWithInProgress = props.copy(inProgress = true)

    stateMachine.test(propsWithInProgress) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }

      awaitBodyMock<RefreshAuthTokensProps> {
        onSuccess(AccountAuthTokensMock)
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<KeysetInitiationNfcResult>>(
        id = "nfc-session"
      ) {
        val sessionResult = KeysetInitiationNfcResult(
          proofOfPossession = HwFactorProofOfPossession("proof"),
          newHwKeys = HwKeyBundle(
            localId = "test-uuid",
            spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
            authKey = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey),
            networkType = FullAccountMock.keybox.config.bitcoinNetworkType
          ),
          sealedSsek = SealedSsekFake,
          ssek = SsekFake
        )
        onSuccess(sessionResult)
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Migration Error")
        secondaryButton.shouldBe(null)
      }
    }
  }

  test("pending transactions warning - Got It navigation returns to intro") {
    // Set up unconfirmed UTXOs
    setTransactionData(
      confirmed = emptySet(),
      unconfirmed = setOf(
        BdkUtxo(
          outPoint = BdkOutPoint(
            txid = "unconfirmed-txid-1",
            vout = 0u
          ),
          txOut = BdkTxOut(
            value = 10000u,
            scriptPubkey = BdkScriptMock()
          ),
          isSpent = false
        )
      )
    )

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> {
        onContinue()
      }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
        }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationPendingTransactionsWarningSheetModel>()
        .apply {
          onGotIt()
        }
      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("pending transactions warning - Back navigation returns to intro") {
    // Set up unconfirmed UTXOs
    setTransactionData(
      confirmed = emptySet(),
      unconfirmed = setOf(
        BdkUtxo(
          outPoint = BdkOutPoint(
            txid = "unconfirmed-txid-1",
            vout = 0u
          ),
          txOut = BdkTxOut(
            value = 10000u,
            scriptPubkey = BdkScriptMock()
          ),
          isSpent = false
        )
      )
    )

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> { onContinue() }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>().apply {
          feeEstimateData.shouldBeInstanceOf<FeeEstimateData.Loading>()
        }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationPendingTransactionsWarningSheetModel>()
        .apply {
          onBack()
        }
      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("UTXO consolidation flow - consolidation required sheet and continue navigation") {
    // Set up more UTXOs than the max limit
    val confirmedUtxos = generateConfirmedUtxos(200)
    setTransactionData(confirmed = confirmedUtxos, unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> { onContinue() }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>()
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationUtxoConsolidationRequiredSheetModel>().apply {
          onContinue()
        }
      awaitBodyMock<UtxoConsolidationProps> {
        context.shouldBe(PrivateWalletMigration)
        // Success returns to intro
        onConsolidationSuccess()
      }
      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("UTXO consolidation flow - back navigation on required sheet returns to intro") {
    val confirmedUtxos = generateConfirmedUtxos(200)
    setTransactionData(confirmed = confirmedUtxos, unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> { onContinue() }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>()
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationUtxoConsolidationRequiredSheetModel>()
        .apply { onBack() }
      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("UTXO consolidation flow - back navigation from consolidation returns to intro") {
    val confirmedUtxos = generateConfirmedUtxos(200)
    setTransactionData(confirmed = confirmedUtxos, unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> { onContinue() }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>()
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationUtxoConsolidationRequiredSheetModel>()
        .apply { onContinue() }
      awaitBodyMock<UtxoConsolidationProps> {
        onBack()
      }
      awaitBody<PrivateWalletMigrationIntroBodyModel>()
    }
  }

  test("fee estimation and consolidation decision priorities - pending transactions warning before consolidation") {
    setTransactionData(
      confirmed = generateConfirmedUtxos(200),
      unconfirmed = setOf(
        BdkUtxo(
          outPoint = BdkOutPoint(
            txid = "unconfirmed-txid-1",
            vout = 0u
          ),
          txOut = BdkTxOut(
            value = 10000u,
            scriptPubkey = BdkScriptMock()
          ),
          isSpent = false
        )
      )
    )
    stateMachine.test(props) {
      awaitBody<PrivateWalletMigrationIntroBodyModel> { onContinue() }
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationFeeEstimateSheetModel>()
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<PrivateWalletMigrationPendingTransactionsWarningSheetModel>()
    }
  }
})
