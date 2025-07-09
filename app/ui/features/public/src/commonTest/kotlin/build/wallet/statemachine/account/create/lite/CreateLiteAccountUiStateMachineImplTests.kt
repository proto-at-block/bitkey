package build.wallet.statemachine.account.create.lite

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.F8eError
import bitkey.onboarding.CreateLiteAccountServiceMock
import bitkey.onboarding.LiteAccountCreationError
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION_FAILURE
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.account.Account
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.ktor.result.HttpError
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.BeTrustedContactIntroductionModel
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CreateLiteAccountUiStateMachineImplTests : FunSpec({

  val createLiteAccountService = CreateLiteAccountServiceMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val appConfigService = AccountConfigServiceFake()
  val stateMachine = CreateLiteAccountUiStateMachineImpl(
    createLiteAccountService = createLiteAccountService,
    trustedContactEnrollmentUiStateMachine = object : TrustedContactEnrollmentUiStateMachine,
      ScreenStateMachineMock<TrustedContactEnrollmentUiProps>(
        "tc-enrollment"
      ) {},
    liteAccountCloudSignInAndBackupUiStateMachine = object :
      LiteAccountCloudSignInAndBackupUiStateMachine,
      ScreenStateMachineMock<LiteAccountCloudSignInAndBackupProps>(
        "cloud-backup"
      ) {},
    deviceInfoProvider = DeviceInfoProviderMock(),
    eventTracker = eventTracker,
    accountConfigService = appConfigService
  )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val propsOnAccountCreatedCalls = turbines.create<Account>("props onDone calls")
  val props = CreateLiteAccountUiProps(
    onBack = { propsOnBackCalls.add(Unit) },
    onAccountCreated = { propsOnAccountCreatedCalls.add(it) },
    inviteCode = null,
    showBeTrustedContactIntroduction = false
  )
  val inviteProps = CreateLiteAccountUiProps(
    onBack = { propsOnBackCalls.add(Unit) },
    onAccountCreated = { propsOnAccountCreatedCalls.add(it) },
    inviteCode = "1234",
    showBeTrustedContactIntroduction = true
  )

  beforeTest {
    createLiteAccountService.reset()
    appConfigService.apply {
      reset()
      setBitcoinNetworkType(SIGNET)
      setF8eEnvironment(Development)
      setIsTestAccount(true)
      setUsingSocRecFakes(true)
    }
    Router.route = null
  }

  test("happy path") {
    val account = createLiteAccountService.createAccountResult.get()!!
    stateMachine.test(props) {
      awaitBody<EnteringInviteCodeBodyModel> {
        onValueChange("code")
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        value.shouldBe("code")
        clickPrimaryButton()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      awaitBodyMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitBodyMock<TrustedContactEnrollmentUiProps> {
        onDone(account)
      }
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(Action.ACTION_APP_SOCREC_ENTERED_INVITE_MANUALLY))
      propsOnAccountCreatedCalls.awaitItem().shouldBe(account)
    }
  }

  test("deep link, no introduction") {
    val account = createLiteAccountService.createAccountResult.get()!!
    stateMachine.test(inviteProps.copy(showBeTrustedContactIntroduction = false)) {
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      awaitBodyMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitBodyMock<TrustedContactEnrollmentUiProps> {
        onDone(account)
      }
      propsOnAccountCreatedCalls.awaitItem().shouldBe(account)
    }
  }

  test("deep link with introduction screen") {
    val account = createLiteAccountService.createAccountResult.get()!!
    stateMachine.test(inviteProps) {
      awaitBody<BeTrustedContactIntroductionModel> {
        onContinue()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      awaitBodyMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitBodyMock<TrustedContactEnrollmentUiProps> {
        onDone(account)
      }
      propsOnAccountCreatedCalls.awaitItem().shouldBe(account)
    }
  }

  test("TC enrollment on retreat calls props.onBack") {
    stateMachine.test(props) {
      awaitBody<EnteringInviteCodeBodyModel> {
        onValueChange("code")
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        clickPrimaryButton()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      awaitBodyMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitBodyMock<TrustedContactEnrollmentUiProps> {
        retreat.onRetreat()
      }

      // Going back will re-register the enter event.
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(Action.ACTION_APP_SOCREC_ENTERED_INVITE_MANUALLY))

      propsOnBackCalls.awaitItem()
    }
  }

  test("non-retryable failure on back calls props.onBack") {
    createLiteAccountService.createAccountResult = Err(NonRetryableError)
    stateMachine.test(props) {
      awaitBody<EnteringInviteCodeBodyModel> {
        onValueChange("code")
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        clickPrimaryButton()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()

      // Going back will re-register the enter event.
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(Action.ACTION_APP_SOCREC_ENTERED_INVITE_MANUALLY))

      awaitBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE) {
        secondaryButton.shouldBeNull()
        clickPrimaryButton()
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        onValueChange("code")
      }
    }
  }

  test("retryable failure returns to code input") {
    createLiteAccountService.createAccountResult = Err(RetryableError)
    stateMachine.test(props) {
      awaitBody<EnteringInviteCodeBodyModel> {
        onValueChange("code")
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        clickPrimaryButton()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(Action.ACTION_APP_SOCREC_ENTERED_INVITE_MANUALLY))
      awaitBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE) {
        clickSecondaryButton()
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        value.shouldBe("code")
      }
    }
  }

  test("retryable failure on retry calls create account again") {
    createLiteAccountService.createAccountResult = Err(RetryableError)
    stateMachine.test(props) {
      awaitBody<EnteringInviteCodeBodyModel> {
        onValueChange("code")
      }
      awaitBody<EnteringInviteCodeBodyModel> {
        clickPrimaryButton()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      awaitBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE) {
        clickPrimaryButton()
      }
      awaitLoadingScreen(NEW_LITE_ACCOUNT_CREATION)
      createLiteAccountService.createAccountCalls.awaitItem()
      awaitBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE)

      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(Action.ACTION_APP_SOCREC_ENTERED_INVITE_MANUALLY))
    }
  }
})

private val NonRetryableError = LiteAccountCreationError.LiteAccountKeyGenerationError(Throwable())
private val RetryableError =
  LiteAccountCreationError.LiteAccountCreationF8eError(
    F8eError.ConnectivityError(HttpError.NetworkError(Throwable()))
  )
