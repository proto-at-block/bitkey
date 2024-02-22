package build.wallet.statemachine.trustedcontact

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_LOAD_KEY
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SUCCESS
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode.RELATIONSHIP_ALREADY_ESTABLISHED
import build.wallet.f8e.error.code.F8eClientErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.NOT_FOUND
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.HttpStatusCode

class TrustedContactEnrollmentUiStateMachineImplTests : FunSpec({

  val socRecKeysRepository = SocRecKeysRepository(SocRecCryptoFake(), SocRecKeysDaoFake())

  val stateMachine =
    TrustedContactEnrollmentUiStateMachineImpl(
      deviceInfoProvider = DeviceInfoProviderMock(),
      socRecKeysRepository = socRecKeysRepository
    )

  var retrieveInvitationResult:
    Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> =
    Ok(InvitationFake)

  var acceptInvitationResult:
    Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> =
    Ok(ProtectedCustomerFake)

  val propsOnRetreatCalls = turbines.create<Unit>("props onRetreat calls")
  val propsOnDoneCalls = turbines.create<Unit>("props onDone calls")
  val props =
    TrustedContactEnrollmentUiProps(
      retreat =
        Retreat(
          style = RetreatStyle.Back,
          onRetreat = { propsOnRetreatCalls.add(Unit) }
        ),
      account = LiteAccountMock,
      inviteCode = null,
      acceptInvitation = { _, _, _ -> acceptInvitationResult },
      retrieveInvitation = { retrieveInvitationResult },
      onDone = { propsOnDoneCalls.add(Unit) },
      screenPresentationStyle = ScreenPresentationStyle.Root
    )

  beforeEach {
    retrieveInvitationResult = Ok(InvitationFake)
    acceptInvitationResult = Ok(ProtectedCustomerFake)
  }

  test("happy path") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E)
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("Some Name", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_LOAD_KEY)
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E)
      awaitScreenWithBody<SuccessBodyModel>(TC_ENROLLMENT_SUCCESS) {
        style.shouldBeTypeOf<SuccessBodyModel.Style.Explicit>()
          .primaryButton.onClick()
      }
      propsOnDoneCalls.awaitItem()
    }
  }

  test("back on enter invite screen calls props.onRetreat") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        toolbar.shouldNotBeNull().leadingAccessory.shouldNotBeNull()
          .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model
          .onClick()

        propsOnRetreatCalls.awaitItem()
      }
    }
  }

  test("initial screen should be enter invite code") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
    }
  }

  context("retrieve invite failure") {
    suspend fun StateMachineTester<TrustedContactEnrollmentUiProps, ScreenModel>.progressToRetrievingInvite() {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E)
    }

    test("specific client error") {
      retrieveInvitationResult = Err(SpecificClientErrorMock(NOT_FOUND))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          primaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("connectivity") {
      retrieveInvitationResult = Err(ConnectivityError())
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldNotBeNull() // Back button
          primaryButton.shouldNotBeNull().onClick() // Retry button
        }
        awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E)
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          primaryButton.shouldNotBeNull() // Retry button
          secondaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("unknown") {
      retrieveInvitationResult = Err(ServerError())
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          primaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }
  }

  context("accept invite failure") {
    suspend fun StateMachineTester<TrustedContactEnrollmentUiProps, ScreenModel>.progressToAcceptingInvite() {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E)
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("Some Name", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_LOAD_KEY)
      awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E)
    }

    test("specific client error") {
      acceptInvitationResult = Err(SpecificClientErrorMock(RELATIONSHIP_ALREADY_ESTABLISHED))
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          primaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("connectivity") {
      acceptInvitationResult = Err(ConnectivityError())
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldNotBeNull() // Back button
          primaryButton.shouldNotBeNull().onClick() // Retry button
        }
        awaitScreenWithBody<LoadingBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E)
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          primaryButton.shouldNotBeNull() // Retry button
          secondaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("unknown") {
      acceptInvitationResult = Err(ServerError())
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          primaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }
  }
})

private fun <T : F8eClientErrorCode> SpecificClientErrorMock(errorCode: T) =
  F8eError.SpecificClientError(
    error = HttpError.ClientError(HttpResponseMock(HttpStatusCode.NotFound)),
    errorCode = errorCode
  )

private fun <T : F8eClientErrorCode> ConnectivityError() =
  F8eError.ConnectivityError<T>(HttpError.NetworkError(Throwable()))

private fun <T : F8eClientErrorCode> ServerError() =
  F8eError.ServerError<T>(
    HttpError.ServerError(HttpResponseMock(HttpStatusCode.InternalServerError))
  )
