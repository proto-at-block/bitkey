package build.wallet.statemachine.recovery.socrec.add

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateTrustedContactInvitationErrorCode
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsService.NotificationStatus.Enabled
import bitkey.notifications.NotificationsServiceMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.relationships.CreateInvitationError
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsProps
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachine
import build.wallet.statemachine.settings.full.notifications.Source
import build.wallet.statemachine.trustedcontact.PromoCodeUpsellUiProps
import build.wallet.statemachine.trustedcontact.PromoCodeUpsellUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*

class AddingTrustedContactUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val exitCalls = turbines.create<Unit>("Exit Calls")
  val invitationSharedCalls = turbines.create<Unit>("Invitation Shared Calls")

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(
    turbine = turbines::create
  )
  val relationshipService = RelationshipsServiceMock(turbines::create, clock)
  val notificationsService = NotificationsServiceMock()

  val proofOfPossessionUIStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {}
  val promoCodeUpsellStateMachine =
    object : PromoCodeUpsellUiStateMachine,
      ScreenStateMachineMock<PromoCodeUpsellUiProps>(id = "promo-code") {}
  val recoveryChannelSettingsStateMachine =
    object : RecoveryChannelSettingsUiStateMachine,
      ScreenStateMachineMock<RecoveryChannelSettingsProps>("recovery-channel-settings") {}

  val stateMachine = AddingTrustedContactUiStateMachineImpl(
    proofOfPossessionNfcStateMachine = proofOfPossessionUIStateMachine,
    inAppBrowserNavigator = inAppBrowserNavigator,
    sharingManager = SharingManagerFake(),
    clipboard = ClipboardMock(),
    promoCodeUpsellUiStateMachine = promoCodeUpsellStateMachine,
    relationshipsService = relationshipService,
    eventTracker = EventTrackerMock(
      turbine = turbines::create
    ),
    notificationChannelStateMachine = recoveryChannelSettingsStateMachine,
    notificationsService = notificationsService
  )

  beforeTest {
    relationshipService.promoCodeResult = Ok(null)
    notificationsService.reset()
  }

  test("Inheritance Invite") {
    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.Beneficiary,
        onAddTc = { _, _ ->
          Ok(
            OutgoingInvitation(
              invitation = BeneficiaryInvitationFake,
              inviteCode = "test"
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      awaitBody<InheritanceInviteSetupBodyModel> {
        onContinue()
      }
      awaitBody<InheritanceInviteExplainerBodyModel> {
        onContinue()
      }
      awaitBody<NameInputBodyModel> {
        onValueChange("Alice")
      }
      awaitUntilBody<NameInputBodyModel>(
        matching = { it.value.isNotBlank() }
      ) {
        primaryButton.onClick()
      }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<SaveContactBodyModel> {
        tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)
      }
      awaitBody<SaveContactBodyModel> {
        onSave()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeInstanceOf<Request.HwKeyProof>().onSuccess(
          HwFactorProofOfPossession("test-token")
        )
      }
      awaitUntilBody<ShareInviteBodyModel> {
        onShareComplete()
      }
      awaitBody<SuccessBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      invitationSharedCalls.awaitItem()
    }
  }

  test("Inheritance Invite -- shows promo") {
    relationshipService.promoCodeResult = Ok(PromotionCode("fake-promo-code"))

    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.Beneficiary,
        onAddTc = { _, _ ->
          Ok(
            OutgoingInvitation(
              invitation = BeneficiaryInvitationFake,
              inviteCode = "test"
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      awaitBody<InheritanceInviteSetupBodyModel> {
        onContinue()
      }
      awaitBody<InheritanceInviteExplainerBodyModel> {
        onContinue()
      }
      awaitBody<NameInputBodyModel> {
        onValueChange("Alice")
      }
      awaitUntilBody<NameInputBodyModel> {
        primaryButton.onClick()
      }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<SaveContactBodyModel> {
        tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)
      }
      awaitBody<SaveContactBodyModel> {
        onSave()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeInstanceOf<Request.HwKeyProof>().onSuccess(
          HwFactorProofOfPossession("test-token")
        )
      }
      awaitUntilBody<ShareInviteBodyModel> {
        onShareComplete()
      }
      awaitBody<SuccessBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<PromoCodeUpsellUiProps> {
        promoCode.shouldBe(relationshipService.promoCodeResult.value)
        onExit()
      }

      invitationSharedCalls.awaitItem()
    }
  }

  test("Recovery Invite") {
    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.SocialRecoveryContact,
        onAddTc = { _, _ ->
          Ok(
            OutgoingInvitation(
              invitation = InvitationFake,
              inviteCode = "test"
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      awaitBody<NameInputBodyModel> {
        onValueChange("Alice")
      }
      awaitUntilBody<NameInputBodyModel>(
        matching = { it.value.isNotBlank() }
      ) {
        primaryButton.onClick()
      }
      awaitBody<SaveContactBodyModel> {
        onSave()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeInstanceOf<Request.HwKeyProof>().onSuccess(
          HwFactorProofOfPossession("test-token")
        )
      }
      awaitUntilBody<ShareInviteBodyModel> {
        onShareComplete()
      }
      awaitBody<SuccessBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      invitationSharedCalls.awaitItem()
    }
  }

  test("Inheritance Invite - Learn more links") {
    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.Beneficiary,
        onAddTc = { _, _ ->
          Ok(
            OutgoingInvitation(
              invitation = InvitationFake,
              inviteCode = "test"
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      // Open learn more link in setup screen:
      awaitBody<InheritanceInviteSetupBodyModel> {
        learnMore()
      }
      awaitBody<InAppBrowserModel> {
        open()
        inAppBrowserNavigator.onOpenCalls.awaitItem()
        inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()
      }
      awaitBody<InheritanceInviteSetupBodyModel> {
        onContinue()
      }
      // Open learn more in explainer screen:
      awaitBody<InheritanceInviteExplainerBodyModel> {
        learnMore()
      }
      awaitBody<InAppBrowserModel> {
        open()
        inAppBrowserNavigator.onOpenCalls.awaitItem()
        inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()
      }
      awaitBody<InheritanceInviteExplainerBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Inheritance Invite - Notifications Disabled") {
    // Reset before test to ensure clean state
    notificationsService.reset()

    val missingChannels =
      setOf(NotificationChannel.Sms, NotificationChannel.Push, NotificationChannel.Email)
    notificationsService.criticalNotificationsStatus.value =
      NotificationsService.NotificationStatus.Missing(missingChannels)

    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.Beneficiary,
        onAddTc = { _, _ ->
          Ok(
            OutgoingInvitation(
              invitation = BeneficiaryInvitationFake,
              inviteCode = "test"
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      awaitBody<InheritanceInviteSetupBodyModel> {
        onContinue()
      }
      awaitBody<InheritanceInviteExplainerBodyModel> {
        onContinue()
      }
      awaitBody<NameInputBodyModel> {
        onValueChange("Alice")
      }
      awaitUntilBody<NameInputBodyModel>(
        matching = { it.value.isNotBlank() }
      ) {
        primaryButton.onClick()
      }

      awaitBody<LoadingSuccessBodyModel>()

      awaitBodyMock<RecoveryChannelSettingsProps> {
        source shouldBe Source.InheritanceStartClaim

        notificationsService.criticalNotificationsStatus.value = Enabled
        onContinue?.invoke()
      }
      awaitBody<SaveContactBodyModel> {
        tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)
      }
      awaitBody<SaveContactBodyModel> {
        onSave()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeInstanceOf<Request.HwKeyProof>().onSuccess(
          HwFactorProofOfPossession("test-token")
        )
      }
      awaitUntilBody<ShareInviteBodyModel> {
        onShareComplete()
      }
      awaitBody<SuccessBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      invitationSharedCalls.awaitItem()
    }
  }

  test("max TCs reached error - Recovery Contact") {
    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.SocialRecoveryContact,
        onAddTc = { _, _ ->
          Err(
            CreateInvitationError.F8ePropagatedError(
              F8eError.SpecificClientError(
                error = HttpError.ClientError(HttpResponseMock(HttpStatusCode.BadRequest)),
                errorCode = CreateTrustedContactInvitationErrorCode.MAX_TRUSTED_CONTACTS_REACHED
              )
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      awaitBody<NameInputBodyModel> {
        onValueChange("Alice")
      }
      awaitUntilBody<NameInputBodyModel>(
        matching = { it.value.isNotBlank() }
      ) {
        primaryButton.onClick()
      }
      awaitBody<SaveContactBodyModel> {
        onSave()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeInstanceOf<Request.HwKeyProof>().onSuccess(
          HwFactorProofOfPossession("test-token")
        )
      }
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        header?.headline?.shouldBe("Maximum limit reached")
        header?.sublineModel?.string?.shouldBe(
          "You can have up to 3 Recovery Contacts. To add a new one, remove one of your current contacts first."
        )
        primaryButton?.text?.shouldBe("Got it")
        secondaryButton.shouldBeNull()
        clickPrimaryButton()
      }
      awaitBody<SaveContactBodyModel>()
    }
  }

  test("max TCs reached error - Beneficiary") {
    stateMachine.test(
      props = AddingTrustedContactUiProps(
        account = FullAccountMock,
        trustedContactRole = TrustedContactRole.Beneficiary,
        onAddTc = { _, _ ->
          Err(
            CreateInvitationError.F8ePropagatedError(
              F8eError.SpecificClientError(
                error = HttpError.ClientError(HttpResponseMock(HttpStatusCode.BadRequest)),
                errorCode = CreateTrustedContactInvitationErrorCode.MAX_TRUSTED_CONTACTS_REACHED
              )
            )
          )
        },
        onInvitationShared = { invitationSharedCalls.add(Unit) },
        onExit = { exitCalls.add(Unit) }
      )
    ) {
      awaitBody<InheritanceInviteSetupBodyModel> {
        onContinue()
      }
      awaitBody<InheritanceInviteExplainerBodyModel> {
        onContinue()
      }
      awaitBody<NameInputBodyModel> {
        onValueChange("Bob")
      }
      awaitUntilBody<NameInputBodyModel>(
        matching = { it.value.isNotBlank() }
      ) {
        primaryButton.onClick()
      }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<SaveContactBodyModel> {
        tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)
      }
      awaitBody<SaveContactBodyModel> {
        onSave()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeInstanceOf<Request.HwKeyProof>().onSuccess(
          HwFactorProofOfPossession("test-token")
        )
      }
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<FormBodyModel> {
        header?.headline?.shouldBe("Maximum limit reached")
        header?.sublineModel?.string?.shouldBe(
          "Currently, you can only have one Beneficiary. To add a new one, remove your current Beneficiary first."
        )
        primaryButton?.text?.shouldBe("Got it")
        secondaryButton.shouldBeNull()
        clickPrimaryButton()
      }
      awaitBody<SaveContactBodyModel>()
    }
  }
})
