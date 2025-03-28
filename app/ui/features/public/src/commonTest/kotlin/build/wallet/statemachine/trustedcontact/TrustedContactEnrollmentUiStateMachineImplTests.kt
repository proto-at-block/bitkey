package build.wallet.statemachine.trustedcontact

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.RELATIONSHIP_ALREADY_ESTABLISHED
import bitkey.f8e.error.code.F8eClientErrorCode
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.NOT_FOUND
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.SealedData
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.relationships.*
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.trustedcontact.model.BeneficiaryOnboardingBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringBenefactorNameBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringProtectedCustomerNameBodyModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
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
import io.ktor.http.HttpStatusCode
import okio.ByteString.Companion.encodeUtf8

class TrustedContactEnrollmentUiStateMachineImplTests : FunSpec({

  val relationshipsCrypto = RelationshipsCryptoFake()
  val relationshipsKeysRepository =
    RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val eventTracker = EventTrackerMock(turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val delegatedDecryptionService = DelegatedDecryptionKeyServiceMock()
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}
  val promoCodeUpsellStateMachine =
    object : PromoCodeUpsellUiStateMachine,
      ScreenStateMachineMock<PromoCodeUpsellUiProps>(id = "promo-code") {}

  val stateMachine =
    TrustedContactEnrollmentUiStateMachineImpl(
      deviceInfoProvider = DeviceInfoProviderMock(),
      relationshipsKeysRepository = relationshipsKeysRepository,
      eventTracker = eventTracker,
      relationshipsService = relationshipsService,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      delegatedDecryptionKeyService = delegatedDecryptionService,
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbine = turbines::create),
      promoCodeUpsellUiStateMachine = promoCodeUpsellStateMachine,
      createAccountUiStateMachine = object : CreateAccountUiStateMachine,
        ScreenStateMachineMock<CreateAccountUiProps>(id = "create-account") {}
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
      screenPresentationStyle = ScreenPresentationStyle.Root,
      variant = TrustedContactFeatureVariant.Direct(
        target = TrustedContactFeatureVariant.Feature.Recovery
      )
    )

  beforeEach {
    relationshipsService.clear()

    relationshipsService.retrieveInvitationResult = Ok(IncomingRecoveryContactInvitationFake)
    relationshipsService.acceptInvitationResult = Ok(ProtectedCustomerFake)
  }

  test("happy path - recovery contact") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<EnteringProtectedCustomerNameBodyModel> {
        onValueChange("Some Name")
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_LOAD_KEY) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_INVITE_ACCEPTED) {
        clickPrimaryButton()
      }
      propsOnDoneCalls.awaitItem()
    }
  }

  test("happy path - beneficiary") {
    relationshipsService.retrieveInvitationResult = Ok(IncomingBeneficiaryInvitationFake)

    stateMachine.test(props) {
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<BeneficiaryOnboardingBodyModel> {
        onContinue()
      }
      awaitBody<EnteringBenefactorNameBodyModel> {
        onValueChange("Some Name")
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_BENEFACTOR_NAME) {
        clickPrimaryButton()
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE) {
        (mainContentList[0] as FormMainContentModel.ListGroup)
          .listGroupModel
          .items
          .first()
          .onClick
          .shouldNotBeNull()
          .invoke()
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE) {
        clickPrimaryButton()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(action = Action.ACTION_APP_SOCREC_BENEFICIARY_HAS_HARDWARE)
      )

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }
      awaitBody<LoadingSuccessBodyModel>(TC_BENEFICIARY_ENROLLMENT_LOAD_KEY) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<LoadingSuccessBodyModel>(TC_BENEFICIARY_ENROLLMENT_UPLOAD_DELEGATED_DECRYPTION_KEY) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBodyMock<NfcSessionUIStateMachineProps<SealedData>> {
        onSuccess("deadbeef".encodeUtf8())
      }
      awaitBody<LoadingSuccessBodyModel>(TC_BENEFICIARY_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel>(TC_BENEFICIARY_ENROLLMENT_INVITE_ACCEPTED) {
        clickPrimaryButton()
      }
      propsOnDoneCalls.awaitItem()
    }
  }

  test("happy path - beneficiary no hardware") {
    relationshipsService.retrieveInvitationResult = Ok(IncomingBeneficiaryInvitationFake)

    stateMachine.test(props) {
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<BeneficiaryOnboardingBodyModel> {
        onContinue()
      }
      awaitBody<EnteringBenefactorNameBodyModel> {
        onValueChange("Some Name")
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_BENEFACTOR_NAME) {
        clickPrimaryButton()
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE) {
        (mainContentList[0] as FormMainContentModel.ListGroup)
          .listGroupModel
          .items[1]
          .onClick
          .shouldNotBeNull()
          .invoke()
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE) {
        clickPrimaryButton()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(action = Action.ACTION_APP_SOCREC_BENEFICIARY_NO_HARDWARE)
      )

      awaitBodyMock<PromoCodeUpsellUiProps> {
        promoCode.shouldBe(relationshipsService.promoCodeResult.value)
        onExit()
        propsOnDoneCalls.awaitItem()
      }
    }
  }

  test("back on enter invite screen calls props.onRetreat") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
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
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
    }
  }

  context("retrieve invite failure") {
    suspend fun StateMachineTester<TrustedContactEnrollmentUiProps, ScreenModel>.progressToRetrievingInvite() {
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }

    test("invalid code") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.InvalidInvitationCode(Error()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("version mismatch") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.InvitationCodeVersionMismatch(Error()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          header?.headline?.shouldBe("Bitkey app out of date")
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("specific client error") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.F8ePropagatedError(SpecificClientErrorMock(NOT_FOUND)))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("connectivity") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.F8ePropagatedError(ConnectivityError()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldNotBeNull() // Back button
          clickPrimaryButton() // Retry button
        }
        awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          primaryButton.shouldNotBeNull() // Retry button
          secondaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("unknown") {
      relationshipsService.retrieveInvitationResult =
        Err(RetrieveInvitationCodeError.F8ePropagatedError(ServerError()))
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }
  }

  context("accept invite failure") {
    suspend fun StateMachineTester<TrustedContactEnrollmentUiProps, ScreenModel>.progressToAcceptingInvite() {
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("Some Name", 0..0)
      }
      awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_LOAD_KEY) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }

    test("invalid code") {
      relationshipsService.acceptInvitationResult =
        Err(AcceptInvitationCodeError.InvalidInvitationCode)
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
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
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("connectivity") {
      relationshipsService.acceptInvitationResult =
        Err(AcceptInvitationCodeError.F8ePropagatedError(ConnectivityError()))
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldNotBeNull() // Back button
          clickPrimaryButton() // Retry button
        }
        awaitBody<LoadingSuccessBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          primaryButton.shouldNotBeNull() // Retry button
          secondaryButton.shouldNotBeNull().onClick() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("unknown") {
      relationshipsService.acceptInvitationResult =
        Err(AcceptInvitationCodeError.F8ePropagatedError(ServerError()))
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
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
