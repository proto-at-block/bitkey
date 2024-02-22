package build.wallet.statemachine.account.create.lite

import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION_FAILURE
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE
import build.wallet.auth.LiteAccountCreationError
import build.wallet.auth.LiteAccountCreatorMock
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.LiteAccountConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.ktor.result.HttpError
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CreateLiteAccountUiStateMachineImplTests : FunSpec({

  val liteAccountCreator = LiteAccountCreatorMock(turbines::create)
  val socRecRepositoryMock = SocRecRelationshipsRepositoryMock(turbines::create)
  val stateMachine =
    CreateLiteAccountUiStateMachineImpl(
      liteAccountCreator = liteAccountCreator,
      trustedContactEnrollmentUiStateMachine =
        object : TrustedContactEnrollmentUiStateMachine, ScreenStateMachineMock<TrustedContactEnrollmentUiProps>(
          "tc-enrollment"
        ) {},
      socRecRelationshipsRepository = socRecRepositoryMock,
      liteAccountCloudSignInAndBackupUiStateMachine =
        object : LiteAccountCloudSignInAndBackupUiStateMachine, ScreenStateMachineMock<LiteAccountCloudSignInAndBackupProps>(
          "cloud-backup"
        ) {}
    )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val propsOnAccountCreatedCalls = turbines.create<LiteAccount>("props onDone calls")
  val props =
    CreateLiteAccountUiProps(
      onBack = { propsOnBackCalls.add(Unit) },
      accountConfig = LiteAccountConfigMock,
      onAccountCreated = { propsOnAccountCreatedCalls.add(it) },
      inviteCode = null
    )
  val inviteProps =
    CreateLiteAccountUiProps(
      onBack = { propsOnBackCalls.add(Unit) },
      accountConfig = LiteAccountConfigMock,
      onAccountCreated = { propsOnAccountCreatedCalls.add(it) },
      inviteCode = "1234"
    )

  beforeTest {
    liteAccountCreator.reset()
  }

  test("happy path") {
    val account = liteAccountCreator.createAccountResult.get()!!
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBodyModelMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitScreenWithBodyModelMock<TrustedContactEnrollmentUiProps> {
        onDone()
      }
      propsOnAccountCreatedCalls.awaitItem().shouldBe(account)
    }
  }

  test("invite code") {
    val account = liteAccountCreator.createAccountResult.get()!!
    stateMachine.test(inviteProps) {
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBodyModelMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitScreenWithBodyModelMock<TrustedContactEnrollmentUiProps> {
        onDone()
      }
      propsOnAccountCreatedCalls.awaitItem().shouldBe(account)
    }
  }

  test("TC enrollment on retreat calls props.onBack") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBodyModelMock<LiteAccountCloudSignInAndBackupProps> {
        onBackupSaved()
      }
      awaitScreenWithBodyModelMock<TrustedContactEnrollmentUiProps> {
        retreat.onRetreat()
      }
      propsOnBackCalls.awaitItem()
    }
  }

  test("non-retryable failure on back calls props.onBack") {
    liteAccountCreator.createAccountResult = Err(NonRetryableError)
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE) {
        secondaryButton.shouldBeNull()
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList
          .first()
          .shouldBeTypeOf<FormMainContentModel.TextInput>()
          .fieldModel
          .value
          .shouldBeEqual("code")
      }
    }
  }

  test("retryable failure returns to code input") {
    liteAccountCreator.createAccountResult = Err(RetryableError)
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE) {
        secondaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList
          .first()
          .shouldBeTypeOf<FormMainContentModel.TextInput>()
          .fieldModel
          .value
          .shouldBeEqual("code")
      }
    }
  }

  test("retryable failure on retry calls create account again") {
    liteAccountCreator.createAccountResult = Err(RetryableError)
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(NEW_LITE_ACCOUNT_CREATION)
      liteAccountCreator.createAccountCalls.awaitItem()
      awaitScreenWithBody<FormBodyModel>(NEW_LITE_ACCOUNT_CREATION_FAILURE)
    }
  }
})

private val NonRetryableError = LiteAccountCreationError.LiteAccountKeyGenerationError(Throwable())
private val RetryableError =
  LiteAccountCreationError.LiteAccountCreationF8eError(
    F8eError.ConnectivityError(HttpError.NetworkError(Throwable()))
  )
