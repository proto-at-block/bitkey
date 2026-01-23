package build.wallet.statemachine.trustedcontact

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.RELATIONSHIP_ALREADY_ESTABLISHED
import bitkey.f8e.error.code.F8eClientErrorCode
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.INVITATION_ROLE_MISMATCH
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode.NOT_FOUND
import bitkey.relationships.Relationships
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.ProtectedBeneficiaryCustomerFake
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.compose.collections.immutableListOf
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
import build.wallet.time.ClockFake
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.*
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.hours

class TrustedContactEnrollmentUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val relationshipsCrypto = RelationshipsCryptoFake()
  val relationshipsKeysRepository =
    RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val eventTracker = EventTrackerMock(turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
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
  val propsOnAccountUpgradedCalls = turbines.create<FullAccount>("props onAccountUpgraded calls")
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
      ),
      onAccountUpgraded = { propsOnAccountUpgradedCalls.add(it) }
    )

  beforeTest {
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

      propsOnAccountUpgradedCalls.awaitItem().shouldBe(FullAccountMock)

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
    suspend fun StateMachineTester<TrustedContactEnrollmentUiProps, ScreenModel>.progressToRetrievingInvite(
      enterScreenId: SocialRecoveryEventTrackerScreenId = TC_ENROLLMENT_ENTER_INVITE_CODE,
      retrieveScreenId: SocialRecoveryEventTrackerScreenId = TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E,
    ) {
      awaitBody<FormBodyModel>(enterScreenId) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().fieldModel
          .onValueChange("code", 0..0)
      }
      awaitBody<FormBodyModel>(enterScreenId) {
        clickPrimaryButton()
      }
      awaitBody<LoadingSuccessBodyModel>(retrieveScreenId) {
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

    test("invitation role mismatch - recovery contact") {
      relationshipsService.retrieveInvitationResult = Err(
        RetrieveInvitationCodeError.F8ePropagatedError(
          SpecificClientErrorMock(INVITATION_ROLE_MISMATCH)
        )
      )
      stateMachine.test(props) {
        progressToRetrievingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          header?.headline?.shouldBe("This code is for a different invitation type")
          header?.sublineModel?.string?.shouldBe(
            "Navigate to Settings > Inheritance > Benefactors > Accept invite and try again."
          )
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("invitation role mismatch - beneficiary") {
      relationshipsService.retrieveInvitationResult = Err(
        RetrieveInvitationCodeError.F8ePropagatedError(
          SpecificClientErrorMock(INVITATION_ROLE_MISMATCH)
        )
      )
      stateMachine.test(
        props.copy(
          variant = TrustedContactFeatureVariant.Direct(
            target = TrustedContactFeatureVariant.Feature.Inheritance
          )
        )
      ) {
        progressToRetrievingInvite(
          enterScreenId = TC_BENEFICIARY_ENROLLMENT_ENTER_INVITE_CODE,
          retrieveScreenId = TC_BENEFICIARY_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
        )
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          header?.headline?.shouldBe("This code is for a different invitation type")
          header?.sublineModel?.string?.shouldBe(
            "Navigate to Security Hub > Recovery Contacts > Accept invite and try again."
          )
          secondaryButton.shouldBeNull() // No retries
          clickPrimaryButton() // Back button
        }
        awaitBody<FormBodyModel>(TC_BENEFICIARY_ENROLLMENT_ENTER_INVITE_CODE)
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

    test("max protected customers reached - recovery contact") {
      relationshipsService.relationshipsFlow.value = Relationships(
        invitations = emptyList(),
        endorsedTrustedContacts = emptyList(),
        unendorsedTrustedContacts = emptyList(),
        protectedCustomers = immutableListOf(ProtectedCustomerFake)
      )
      relationshipsService.acceptInvitationResult = Err(
        AcceptInvitationCodeError.F8ePropagatedError(
          SpecificClientErrorMock(
            bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.MAX_PROTECTED_CUSTOMERS_REACHED
          )
        )
      )
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          header?.headline?.shouldBe("Maximum limit reached")
          header?.sublineModel?.string?.shouldBe(
            "You’re already a Recovery Contact for 20 people. To accept this invite, remove yourself as a Recovery Contact for someone first."
          )
          primaryButton?.text?.shouldBe("Got it")
          secondaryButton.shouldBeNull()
          clickPrimaryButton()
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      }
    }

    test("max protected customers reached - beneficiary") {
      relationshipsService.relationshipsFlow.value = Relationships(
        invitations = emptyList(),
        endorsedTrustedContacts = emptyList(),
        unendorsedTrustedContacts = emptyList(),
        protectedCustomers = immutableListOf(ProtectedBeneficiaryCustomerFake)
      )
      relationshipsService.retrieveInvitationResult = Ok(IncomingBeneficiaryInvitationFake)
      relationshipsService.acceptInvitationResult = Err(
        AcceptInvitationCodeError.F8ePropagatedError(
          SpecificClientErrorMock(
            bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.MAX_PROTECTED_CUSTOMERS_REACHED
          )
        )
      )

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

        propsOnAccountUpgradedCalls.awaitItem().shouldBe(FullAccountMock)

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
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          header?.headline?.shouldBe("Maximum limit reached")
          header?.sublineModel?.string?.shouldBe(
            "You’re already a Beneficiary for 20 people. To accept this invite, remove a Benefactor first."
          )
          primaryButton?.text?.shouldBe("Got it")
          secondaryButton.shouldBeNull()
          clickPrimaryButton()
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_TC_ADD_BENEFACTOR_NAME)
      }
    }

    test("max protected customers reached - mixed roles") {
      relationshipsService.relationshipsFlow.value = Relationships(
        invitations = emptyList(),
        endorsedTrustedContacts = emptyList(),
        unendorsedTrustedContacts = emptyList(),
        protectedCustomers = immutableListOf(
          ProtectedCustomerFake,
          ProtectedBeneficiaryCustomerFake
        )
      )
      relationshipsService.acceptInvitationResult = Err(
        AcceptInvitationCodeError.F8ePropagatedError(
          SpecificClientErrorMock(
            bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode.MAX_PROTECTED_CUSTOMERS_REACHED
          )
        )
      )
      stateMachine.test(props) {
        progressToAcceptingInvite()
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E_FAILURE) {
          header?.headline?.shouldBe("Maximum limit reached")
          header?.sublineModel?.string?.shouldBe(
            "You’re already a Recovery Contact and/or Beneficiary for 20 people. To accept this invite, remove yourself as a Recovery Contact or remove a Benefactor first."
          )
          primaryButton?.text?.shouldBe("Got it")
          secondaryButton.shouldBeNull()
          clickPrimaryButton()
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

  test("onAccountUpgraded callback is invoked when lite account is upgraded during beneficiary enrollment") {
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
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()
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
      propsOnAccountUpgradedCalls.awaitItem().shouldBe(FullAccountMock)
      awaitBody<LoadingSuccessBodyModel>(TC_BENEFICIARY_ENROLLMENT_LOAD_KEY)
      cancelAndIgnoreRemainingEvents()
    }
  }

  context("expired invitation") {
    test("expired recovery contact invitation - shows error and goes back to invite code entry") {
      relationshipsService.retrieveInvitationResult = Ok(
        IncomingRecoveryContactInvitationFake.copy(
          expiresAt = clock.now().minus(1.hours)
        )
      )

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
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          header?.headline?.shouldBe("This code has expired")
          secondaryButton.shouldBeNull()
          clickPrimaryButton()
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
      }
    }

    test("expired beneficiary invitation - shows error and goes back to invite code entry") {
      relationshipsService.retrieveInvitationResult = Ok(
        IncomingBeneficiaryInvitationFake.copy(
          expiresAt = clock.now().minus(1.hours)
        )
      )

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
        awaitBody<FormBodyModel>(TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E_FAILURE) {
          header?.headline?.shouldBe("This code has expired")
          secondaryButton.shouldBeNull()
          clickPrimaryButton()
        }
        awaitBody<FormBodyModel>(TC_ENROLLMENT_ENTER_INVITE_CODE)
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
