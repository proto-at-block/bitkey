package build.wallet.statemachine.moneyhome.full

import app.cash.turbine.plusAssign
import bitkey.securitycenter.SecurityActionsServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsActivityServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProviderFake
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.platform.haptics.HapticsMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsProps
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachine
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState
import build.wallet.statemachine.partnerships.AddBitcoinUiProps
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachine
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkProps
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkUiStateMachine
import build.wallet.statemachine.transactions.TransactionsActivityModel
import build.wallet.statemachine.transactions.TransactionsActivityProps
import build.wallet.statemachine.transactions.TransactionsActivityUiStateMachine
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationProps
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationUiStateMachine
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactProps
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationServiceFake
import build.wallet.wallet.migration.MigrationType
import build.wallet.worker.RefreshExecutor
import build.wallet.worker.RefreshOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class MoneyHomeViewingBalanceUiStateMachineImplTests : FunSpec({
  val coachmarkService = CoachmarkServiceMock(
    defaultCoachmarks = emptyList(),
    turbineFactory = turbines::create
  )
  val eventTracker = EventTrackerMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val haptics = HapticsMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val securityActionsService = SecurityActionsServiceFake()
  val firmwareDataService = FirmwareDataServiceFake()
  val migrationService = MigrationServiceFake()
  val featureFlagDao = FeatureFlagDaoMock()
  val bip177FeatureFlag = Bip177FeatureFlag(featureFlagDao)
  val designSystemUpdatesFeatureFlag = DesignSystemUpdatesFeatureFlag(featureFlagDao)
  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake()

  val setStateCalls = turbines.create<MoneyHomeUiState>("setState calls")
  val onSettingsCalls = turbines.create<Unit>("onSettings calls")
  val transactionsActivityService = TransactionsActivityServiceFake()

  val transactionsActivityUiStateMachine = object : TransactionsActivityUiStateMachine,
    StateMachineMock<TransactionsActivityProps, TransactionsActivityModel?>(null) {}

  val props = MoneyHomeViewingBalanceUiProps(
    account = FullAccountMock,
    homeStatusBannerModel = null,
    onSettings = { onSettingsCalls += Unit },
    onPartnershipsWebFlowCompleted = { _, _ -> },
    state = ViewingBalanceUiState(),
    setState = { setStateCalls += it },
    onStartSweepFlow = {},
    onGoToSecurityHub = {},
    onGoToPrivateWalletMigration = {},
    onPurchaseAmountConfirmed = {}
  )

  val stateMachine = MoneyHomeViewingBalanceUiStateMachineImpl(
    addBitcoinUiStateMachine = object : AddBitcoinUiStateMachine,
      ScreenStateMachineMock<AddBitcoinUiProps>("add-bitcoin") {},
    appFunctionalityService = appFunctionalityService,
    eventTracker = eventTracker,
    moneyDisplayFormatter = moneyDisplayFormatter,
    gettingStartedTaskDao = gettingStartedTaskDao,
    moneyHomeCardsUiStateMachine = object : MoneyHomeCardsUiStateMachine,
      StateMachineMock<MoneyHomeCardsProps, CardListModel>(CardListModel(cards = immutableListOf())) {},
    transactionsActivityUiStateMachine = transactionsActivityUiStateMachine,
    viewingInvitationUiStateMachine = object : ViewingInvitationUiStateMachine,
      ScreenStateMachineMock<ViewingInvitationProps>("viewing-invitation") {},
    viewingRecoveryContactUiStateMachine = object : ViewingRecoveryContactUiStateMachine,
      ScreenStateMachineMock<ViewingRecoveryContactProps>("viewing-recovery-contact") {},
    moneyHomeHiddenStatusProvider = MoneyHomeHiddenStatusProviderFake(),
    coachmarkService = coachmarkService,
    haptics = haptics,
    firmwareDataService = firmwareDataService,
    bitcoinWalletService = bitcoinWalletService,
    transactionsActivityService = transactionsActivityService,
    inAppBrowserNavigator = inAppBrowserNavigator,
    securityActionsService = securityActionsService,
    refreshExecutor = object : RefreshExecutor {
      override suspend fun runRefreshOperation(refreshOperation: RefreshOperation) {}
    },
    partnerTransferLinkUiStateMachine = object : PartnerTransferLinkUiStateMachine,
      ScreenStateMachineMock<PartnerTransferLinkProps>("partner-transfer-link") {},
    migrationService = migrationService,
    bip177FeatureFlag = bip177FeatureFlag,
    designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag,
    bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository
  )

  beforeTest {
    coachmarkService.reset()
    bitcoinWalletService.reset()
    migrationService.reset()
  }

  test("displays PrivateWalletHomeCoachmark when available") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.PrivateWalletHomeCoachmark
    )
    migrationService.resumeResult = com.github.michaelbull.result.Ok(
      MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration)
    )
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    stateMachine.test(props) {
      // initial screen while fetching coachmarks
      awaitBody<MoneyHomeBodyModel>()

      // Should show PrivateWalletHomeCoachmark
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        coachmark.dismiss()
      }

      coachmarkService.markDisplayedTurbine.awaitItem().shouldBe(
        CoachmarkIdentifier.PrivateWalletHomeCoachmark
      )

      // No more coachmarks
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldBeNull()
      }
    }
  }

  test("coachmarks are not shown until balance is loaded") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.Bip177Coachmark,
      CoachmarkIdentifier.PrivateWalletHomeCoachmark
    )
    migrationService.resumeResult = com.github.michaelbull.result.Ok(
      MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration)
    )

    stateMachine.test(props) {
      // Coachmark should be null while balance is loading
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldBeNull()
      }

      // Simulate balance loading complete
      bitcoinWalletService.transactionsData.value = TransactionsDataMock

      // Coachmark should now appear
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(CoachmarkIdentifier.Bip177Coachmark)
      }
    }
  }

  test("displays W3 upgrade completion coachmark when launched from completed upgrade") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.W3UpgradeCompleteCoachmark
    )
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    stateMachine.test(
      props.copy(
        state = ViewingBalanceUiState(showW3UpgradeCompleteCoachmark = true)
      )
    ) {
      awaitBody<MoneyHomeBodyModel>()

      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(CoachmarkIdentifier.W3UpgradeCompleteCoachmark)
        coachmark.title.shouldBe("Your wallet is ready")
        coachmark.description.shouldBe("Start using your new Bitkey device anytime.")
        coachmark.dismiss()
      }

      setStateCalls.awaitItem().shouldBe(
        ViewingBalanceUiState(showW3UpgradeCompleteCoachmark = false)
      )
      coachmarkService.markDisplayedTurbine.awaitItem().shouldBe(
        CoachmarkIdentifier.W3UpgradeCompleteCoachmark
      )

      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldBeNull()
      }
    }
  }

  test("does not display W3 upgrade completion coachmark when coachmark service suppresses it") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    stateMachine.test(
      props.copy(
        state = ViewingBalanceUiState(showW3UpgradeCompleteCoachmark = true)
      )
    ) {
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldBeNull()
      }

      coachmarkService.markDisplayedTurbine.expectNoEvents()
      setStateCalls.expectNoEvents()
      eventTracker.eventCalls.expectNoEvents()
    }
  }

  test("does not request W3 upgrade completion coachmark until balance data is loaded") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.W3UpgradeCompleteCoachmark
    )
    bitcoinWalletService.transactionsData.value = null

    stateMachine.test(
      props.copy(
        state = ViewingBalanceUiState(showW3UpgradeCompleteCoachmark = true)
      )
    ) {
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldBeNull()
      }

      coachmarkService.coachmarksToDisplayRequestsTurbine.awaitUntil(
        setOf(
          CoachmarkIdentifier.Bip177Coachmark,
          CoachmarkIdentifier.PrivateWalletHomeCoachmark
        )
      )

      bitcoinWalletService.transactionsData.value = TransactionsDataMock

      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(CoachmarkIdentifier.W3UpgradeCompleteCoachmark)
      }

      coachmarkService.coachmarksToDisplayRequestsTurbine.awaitUntil(
        setOf(
          CoachmarkIdentifier.Bip177Coachmark,
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          CoachmarkIdentifier.W3UpgradeCompleteCoachmark
        )
      )
    }
  }

  test("shows other available money home coachmarks when W3 upgrade coachmark is unavailable") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.Bip177Coachmark
    )
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    stateMachine.test(
      props.copy(
        state = ViewingBalanceUiState(showW3UpgradeCompleteCoachmark = true)
      )
    ) {
      awaitBody<MoneyHomeBodyModel>()

      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(
          CoachmarkIdentifier.Bip177Coachmark
        )
      }
      coachmarkService.markDisplayedTurbine.expectNoEvents()
      setStateCalls.expectNoEvents()
      eventTracker.eventCalls.expectNoEvents()
    }
  }

  test("transactions list shows empty state when no transactions and not loading") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    stateMachine.test(props.copy(isDesignSystemV2Enabled = true)) {
      awaitBody<MoneyHomeBodyModel> {
        transactionsModel.shouldNotBeNull()
        transactionsModel.headerText.shouldBe("Recent activity")
        transactionsModel.sections.shouldBeEmpty()
      }
    }
  }

  test("transactions list is hidden when empty and V2 disabled") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    stateMachine.test(props.copy(isDesignSystemV2Enabled = false)) {
      awaitBody<MoneyHomeBodyModel> {
        transactionsModel.shouldBeNull()
      }
    }
  }

  test("transactions list is null when loading") {
    // ensure transactionsData is null to simulate loading
    bitcoinWalletService.transactionsData.value = null

    stateMachine.test(props) {
      awaitBody<MoneyHomeBodyModel> {
        transactionsModel.shouldBeNull()
      }
    }
  }

  test("transactions list shows transactions when they exist and V2 enabled") {
    val mockModel = TransactionsActivityModel(
      listModel = build.wallet.ui.model.list.ListGroupModel(
        items = immutableListOf(
          build.wallet.ui.model.list.ListItemModel(title = "Tx 1")
        ),
        style = build.wallet.ui.model.list.ListGroupStyle.NONE
      ),
      hasMoreTransactions = false
    )
    transactionsActivityUiStateMachine.emitModel(mockModel)

    stateMachine.test(props.copy(isDesignSystemV2Enabled = true)) {
      awaitBody<MoneyHomeBodyModel> {
        transactionsModel.shouldNotBeNull()
        // It should contain the sections from mockModel
        transactionsModel.sections.shouldNotBeEmpty()
      }
    }
  }
})
