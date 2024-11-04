package build.wallet.statemachine.trustedcontact

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.*
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode.RELATIONSHIP_ALREADY_ESTABLISHED
import build.wallet.f8e.error.code.F8eClientErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.NOT_FOUND
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.relationships.*
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.trustedcontact.model.EnteringProtectedCustomerNameBodyModel
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.*

class TrustedContactEnrollmentUiStateMachineImplTests : FunSpec({

  val relationshipsCrypto = RelationshipsCryptoFake()
  val relationshipsKeysRepository = RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val eventTracker = EventTrackerMock(turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create)

  val stateMachine =
    TrustedContactEnrollmentUiStateMachineImpl(
      deviceInfoProvider = DeviceInfoProviderMock(),
      relationshipsKeysRepository = relationshipsKeysRepository,
      eventTracker = eventTracker,
      relationshipsService = relationshipsService
    )

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
      onDone = { propsOnDoneCalls.add(Unit) },
      screenPresentationStyle = ScreenPresentationStyle.Root
    )

  beforeEach {
    relationshipsService.clear()

    relationshipsService.retrieveInvitationResult = Ok(IncomingInvitationFake)
    relationshipsService.acceptInvitationResult = Ok(ProtectedCustomerFake)
  }

  test("happy path") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<EnteringProtectedCustomerNameBodyModel> {
        onValueChange("Some Name")
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_LOAD_KEY) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_SUCCESS) {
        clickPrimaryButton()
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
        clickPrimaryButton()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }

    test("invalid code") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.InvalidInvitationCode(Error()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("version mismatch") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.InvitationCodeVersionMismatch(Error()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          header?.headline?.shouldBe("Bitkey app out of date")
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("specific client error") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.F8ePropagatedError(SpecificClientErrorMock(NOT_FOUND)))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("connectivity") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.F8ePropagatedError(ConnectivityError()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldNotBeNull() // Back button
          clickPrimaryButton() // Retry button
        }
        awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          primaryButton.shouldNotBeNull() // Retry button
          secondaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("unknown") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.F8ePropagatedError(ServerError()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
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
        clickPrimaryButton()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("Some Name", 0..0)
      }
      awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_LOAD_KEY) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }

    test("invalid code") {
      relationshipsService.acceptInvitationResult = Err(AcceptInvitationCodeError.InvalidInvitationCode)
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("specific client error") {
      relationshipsService.acceptInvitationResult = Err(
        AcceptInvitationCodeError.F8ePropagatedError(
          SpecificClientErrorMock(RELATIONSHIP_ALREADY_ESTABLISHED)
        )
      )
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("connectivity") {
      relationshipsService.acceptInvitationResult =
        Err(AcceptInvitationCodeError.F8ePropagatedError(ConnectivityError()))
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldNotBeNull() // Back button
          clickPrimaryButton() // Retry button
        }
        awaitScreenWithBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          primaryButton.shouldNotBeNull() // Retry button
          secondaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("unknown") {
      relationshipsService.acceptInvitationResult =
        Err(AcceptInvitationCodeError.F8ePropagatedError(ServerError()))
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitScreenWithBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
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
