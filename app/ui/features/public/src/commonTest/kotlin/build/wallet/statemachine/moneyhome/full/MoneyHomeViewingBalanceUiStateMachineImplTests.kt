package build.wallet.statemachine.moneyhome.full

import app.cash.turbine.plusAssign
import bitkey.securitycenter.SecurityActionsServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsActivityServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProviderFake
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
import build.wallet.wallet.migration.PrivateWalletMigrationServiceFake
import build.wallet.wallet.migration.PrivateWalletMigrationState
import build.wallet.worker.RefreshExecutor
import build.wallet.worker.RefreshOperation
import io.kotest.core.spec.style.FunSpec
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
  val privateWalletMigrationService = PrivateWalletMigrationServiceFake()

  val setStateCalls = turbines.create<MoneyHomeUiState>("setState calls")
  val onSettingsCalls = turbines.create<Unit>("onSettings calls")
  val transactionsActivityService = TransactionsActivityServiceFake()

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
    transactionsActivityUiStateMachine = object : TransactionsActivityUiStateMachine,
      StateMachineMock<TransactionsActivityProps, TransactionsActivityModel?>(null) {},
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
    privateWalletMigrationService = privateWalletMigrationService
  )

  beforeTest {
    coachmarkService.reset()
    bitcoinWalletService.reset()
    privateWalletMigrationService.reset()
  }

  test("displays coachmarks in priority order") {
    // Set all coachmarks as displayable
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.BalanceGraphCoachmark,
      CoachmarkIdentifier.SecurityHubHomeCoachmark,
      CoachmarkIdentifier.PrivateWalletHomeCoachmark
    )
    privateWalletMigrationService.migrationState.value = PrivateWalletMigrationState.Available

    stateMachine.test(props) {
      // initial screen while fetching coachmarks
      awaitBody<MoneyHomeBodyModel>()

      // First should be BalanceGraphCoachmark
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(CoachmarkIdentifier.BalanceGraphCoachmark)
        coachmark.dismiss()
      }

      coachmarkService.markDisplayedTurbine.awaitItem().shouldBe(
        CoachmarkIdentifier.BalanceGraphCoachmark
      )

      // Next should be SecurityHubHomeCoachmark
      awaitBody<MoneyHomeBodyModel> {
        coachmark.shouldNotBeNull()
        coachmark.identifier.shouldBe(CoachmarkIdentifier.SecurityHubHomeCoachmark)
        coachmark.dismiss()
      }

      coachmarkService.markDisplayedTurbine.awaitItem().shouldBe(
        CoachmarkIdentifier.SecurityHubHomeCoachmark
      )

      // Finally PrivateWalletHomeCoachmark
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
})
