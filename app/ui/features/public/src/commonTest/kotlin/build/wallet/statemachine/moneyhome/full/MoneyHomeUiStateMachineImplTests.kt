package build.wallet.statemachine.moneyhome.full

import bitkey.ui.framework.NavigatorPresenterFake
import build.wallet.bitcoin.invoice.PaymentDataParserMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.money.FiatMoney
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.links.DeepLinkHandlerMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.DeclineInheritanceClaimUiProps
import build.wallet.statemachine.inheritance.DeclineInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachine
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiStateMachine
import build.wallet.statemachine.partnerships.sell.PartnershipsSellUiProps
import build.wallet.statemachine.partnerships.sell.PartnershipsSellUiStateMachine
import build.wallet.statemachine.pricechart.BitcoinPriceChartUiProps
import build.wallet.statemachine.pricechart.BitcoinPriceChartUiStateMachine
import build.wallet.statemachine.receive.AddressQrCodeUiProps
import build.wallet.statemachine.receive.AddressQrCodeUiStateMachine
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.transactions.FailedPartnerTransactionProps
import build.wallet.statemachine.transactions.FailedPartnerTransactionUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetailsUiProps
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachine
import build.wallet.statemachine.transactions.TransactionsActivityModel
import build.wallet.statemachine.transactions.TransactionsActivityProps
import build.wallet.statemachine.transactions.TransactionsActivityUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiProps
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiStateMachine
import build.wallet.statemachine.walletmigration.W3UpgradeUiProps
import build.wallet.statemachine.walletmigration.W3UpgradeUiStateMachine
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationServiceFake
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class MoneyHomeUiStateMachineImplTests : FunSpec({
  val moneyHomeViewingBalanceUiStateMachine = object : MoneyHomeViewingBalanceUiStateMachine,
    ScreenStateMachineMock<MoneyHomeViewingBalanceUiProps>(
      "money-home-viewing-balance"
    ) {}

  val customAmountEntryUiStateMachine = object : CustomAmountEntryUiStateMachine,
    ScreenStateMachineMock<CustomAmountEntryUiProps>(
      "custom-amount-entry"
    ) {}

  val defaultW3UpgradeUiStateMachine = object : W3UpgradeUiStateMachine,
    ScreenStateMachineMock<W3UpgradeUiProps>("w3-upgrade") {}

  val baseMigrationService = MigrationServiceFake()
  var resumeOverride: ((MigrationType) -> Result<MigrationProgress, MigrationError>)? = null
  val defaultMigrationService = object : MigrationService by baseMigrationService {
    override suspend fun resume(
      type: MigrationType,
    ): Result<MigrationProgress, MigrationError> {
      baseMigrationService.resumeCalls.add(type)
      return resumeOverride?.invoke(type) ?: baseMigrationService.resumeResult
    }
  }
  val featureFlagDao = FeatureFlagDaoFake()

  fun createStateMachine(
    migrationService: MigrationService = defaultMigrationService,
    w3UpgradeUiStateMachine: W3UpgradeUiStateMachine = defaultW3UpgradeUiStateMachine,
  ) = MoneyHomeUiStateMachineImpl(
    addressQrCodeUiStateMachine = object : AddressQrCodeUiStateMachine,
      ScreenStateMachineMock<AddressQrCodeUiProps>("address-qr-code") {},
    sendUiStateMachine = object : SendUiStateMachine,
      ScreenStateMachineMock<SendUiProps>("send") {},
    transactionDetailsUiStateMachine = object : TransactionDetailsUiStateMachine,
      ScreenStateMachineMock<TransactionDetailsUiProps>("transaction-details") {},
    transactionsActivityUiStateMachine = object : TransactionsActivityUiStateMachine,
      StateMachineMock<TransactionsActivityProps, TransactionsActivityModel?>(null) {},
    lostHardwareUiStateMachine = object : LostHardwareRecoveryUiStateMachine,
      ScreenStateMachineMock<LostHardwareRecoveryProps>("lost-hardware") {},
    setSpendingLimitUiStateMachine = object : SetSpendingLimitUiStateMachine,
      ScreenStateMachineMock<SpendingLimitProps>("set-spending-limit") {},
    inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
    clipboard = ClipboardMock(),
    paymentDataParser = PaymentDataParserMock(),
    recoveryIncompleteRepository = PostSocRecTaskRepositoryMock(),
    moneyHomeViewingBalanceUiStateMachine = moneyHomeViewingBalanceUiStateMachine,
    customAmountEntryUiStateMachine = customAmountEntryUiStateMachine,
    sweepUiStateMachine = object : SweepUiStateMachine,
      ScreenStateMachineMock<SweepUiProps>("sweep") {},
    bitcoinPriceChartUiStateMachine = object : BitcoinPriceChartUiStateMachine,
      ScreenStateMachineMock<BitcoinPriceChartUiProps>("price-chart") {},
    socRecService = SocRecServiceFake(),
    utxoConsolidationUiStateMachine = object : UtxoConsolidationUiStateMachine,
      ScreenStateMachineMock<UtxoConsolidationProps>("utxo-consolidation") {},
    partnershipsSellUiStateMachine = object : PartnershipsSellUiStateMachine,
      ScreenStateMachineMock<PartnershipsSellUiProps>("partnerships-sell") {},
    failedPartnerTransactionUiStateMachine = object : FailedPartnerTransactionUiStateMachine,
      ScreenStateMachineMock<FailedPartnerTransactionProps>("failed-partner-transaction") {},
    completeClaimUiStateMachine = object : CompleteInheritanceClaimUiStateMachine,
      ScreenStateMachineMock<CompleteInheritanceClaimUiStateMachineProps>("complete-claim") {},
    declineInheritanceClaimUiStateMachine = object : DeclineInheritanceClaimUiStateMachine,
      ScreenStateMachineMock<DeclineInheritanceClaimUiProps>("decline-claim") {},
    onboardingCompletionService = OnboardingCompletionServiceFake(),
    navigatorPresenter = NavigatorPresenterFake(),
    migrationService = migrationService,
    privateWalletMigrationUiStateMachine = object : PrivateWalletMigrationUiStateMachine,
      ScreenStateMachineMock<PrivateWalletMigrationUiProps>("private-wallet-migration") {},
    recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create),
    clock = Clock.System,
    partnershipsPurchaseQuotesUiStateMachine = object : PartnershipsPurchaseQuotesUiStateMachine,
      ScreenStateMachineMock<PartnershipsPurchaseQuotesUiProps>("purchase-quotes") {},
    deepLinkHandler = DeepLinkHandlerMock(turbines::create),
    w3UpgradeUiStateMachine = w3UpgradeUiStateMachine
  )

  val stateMachine = createStateMachine()

  val props = MoneyHomeUiProps(
    account = FullAccountMock,
    homeStatusBannerModel = null,
    onSettings = {},
    onPartnershipsWebFlowCompleted = { _, _ -> },
    origin = MoneyHomeUiProps.Origin.Launch,
    onDismissOrigin = {},
    onGoToSecurityHub = {}
  )

  beforeTest {
    baseMigrationService.reset()
    resumeOverride = null
    featureFlagDao.reset()
  }

  test("backing out of custom amount returns to money home without reopening buy sheet") {
    stateMachine.test(props) {
      awaitUntilBodyMock<MoneyHomeViewingBalanceUiProps>(id = moneyHomeViewingBalanceUiStateMachine.id) {
        setState(
          MoneyHomeUiState.SelectCustomPartnerPurchaseAmountState(
            minimumAmount = FiatMoney.usd(10.0),
            maximumAmount = FiatMoney.usd(1000.0)
          )
        )
      }

      awaitBodyMock<CustomAmountEntryUiProps>(id = customAmountEntryUiStateMachine.id) {
        onBack()
      }

      awaitUntilBodyMock<MoneyHomeViewingBalanceUiProps>(id = moneyHomeViewingBalanceUiStateMachine.id) {
        state.shouldBe(MoneyHomeUiState.ViewingBalanceUiState())
      }
    }
  }

  test("launch auto-routes to W3 upgrade when only W3 migration is in progress") {
    val w3UpgradeProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = FullAccountMock.keybox.activeSpendingKeyset
    )
    resumeOverride = { type ->
      Ok(
        when (type) {
          MigrationType.PrivateWalletMigration -> MigrationProgress.NotStarted(type)
          MigrationType.W3Upgrade -> w3UpgradeProgress
        }
      )
    }

    stateMachine.test(props) {
      awaitUntilBodyMock<W3UpgradeUiProps>(id = defaultW3UpgradeUiStateMachine.id) {
        account.shouldBe(FullAccountMock)
      }
    }
  }

  test("launch auto-routes to private wallet migration with resume progress") {
    val privateWalletProgress = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = FullAccountMock.keybox.activeSpendingKeyset
    )
    resumeOverride = { type ->
      Ok(
        when (type) {
          MigrationType.PrivateWalletMigration -> privateWalletProgress
          MigrationType.W3Upgrade -> MigrationProgress.NotStarted(type)
        }
      )
    }

    stateMachine.test(props) {
      awaitUntilBodyMock<PrivateWalletMigrationUiProps>(id = "private-wallet-migration") {
        account.shouldBe(FullAccountMock)
        resumeProgress.shouldBe(privateWalletProgress)
      }
    }
  }

  test("launch auto-routes to W3 upgrade when cloud restore persisted a W3 placeholder") {
    resumeOverride = { type ->
      Ok(
        when (type) {
          MigrationType.PrivateWalletMigration -> MigrationProgress.NotStarted(type)
          MigrationType.W3Upgrade ->
            MigrationProgress.NotStarted(type, resumedFromCloudBackup = true)
        }
      )
    }

    stateMachine.test(props) {
      awaitUntilBodyMock<W3UpgradeUiProps>(id = defaultW3UpgradeUiStateMachine.id) {
        account.shouldBe(FullAccountMock)
      }
    }
  }

  test("completed resumed W3 upgrade returns to money home with success sheet") {
    val w3UpgradeProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = FullAccountMock.keybox.activeSpendingKeyset
    )
    resumeOverride = { type ->
      Ok(
        when (type) {
          MigrationType.PrivateWalletMigration -> MigrationProgress.NotStarted(type)
          MigrationType.W3Upgrade -> w3UpgradeProgress
        }
      )
    }

    stateMachine.test(props) {
      awaitUntilBodyMock<W3UpgradeUiProps>(id = defaultW3UpgradeUiStateMachine.id) {
        onUpgradeComplete(FullAccountMock)
      }

      awaitUntilBodyMock<MoneyHomeViewingBalanceUiProps>(id = moneyHomeViewingBalanceUiStateMachine.id) {
        state.shouldBe(
          MoneyHomeUiState.ViewingBalanceUiState(
            bottomSheetDisplayState =
              MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.W3UpgradeComplete
          )
        )
      }
    }
  }

  test("W3 upgrade completion origin opens the success sheet") {
    stateMachine.test(props.copy(origin = MoneyHomeUiProps.Origin.W3UpgradeComplete)) {
      awaitUntilBodyMock<MoneyHomeViewingBalanceUiProps>(id = moneyHomeViewingBalanceUiStateMachine.id) {
        state.shouldBe(
          MoneyHomeUiState.ViewingBalanceUiState(
            bottomSheetDisplayState =
              MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.W3UpgradeComplete
          )
        )
      }
    }
  }
})
