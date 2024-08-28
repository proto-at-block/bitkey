package build.wallet.statemachine.recovery.socrec.list.full

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.recovery.socrec.SocRecServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.*
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant

class ListingTrustedContactsUiStateMachineImplTests : FunSpec({
  val socRecService = SocRecServiceMock(turbines::create)

  val listingTrustedContactsUiStateMachine =
    ListingTrustedContactsUiStateMachineImpl(
      viewingRecoveryContactUiStateMachine = object : ViewingRecoveryContactUiStateMachine,
        ScreenStateMachineMock<ViewingRecoveryContactProps>(
          "viewing-recovery-contact"
        ) {},
      viewingInvitationUiStateMachine = object : ViewingInvitationUiStateMachine,
        ScreenStateMachineMock<ViewingInvitationProps>(
          "viewing-invitation"
        ) {},
      viewingProtectedCustomerUiStateMachine = object : ViewingProtectedCustomerUiStateMachine,
        ScreenStateMachineMock<ViewingProtectedCustomerProps>(
          "viewing-protected-customer"
        ) {},
      helpingWithRecoveryUiStateMachine = object : HelpingWithRecoveryUiStateMachine,
        ScreenStateMachineMock<HelpingWithRecoveryUiProps>(
          "helping-with-recovery"
        ) {},
      clock = ClockFake(),
      socRecService = socRecService
    )
  val onExitCalls = turbines.create<Unit>("onExit")
  val onAddTCCalls = turbines.create<Unit>("onAddTC")
  val relationships = SocRecRelationships.EMPTY
  val props =
    ListingTrustedContactsUiProps(
      account = FullAccountMock,
      onAddTCButtonPressed = { onAddTCCalls.add(Unit) },
      onAcceptTrustedContactInvite = {},
      onExit = { onExitCalls.add(Unit) }
    )

  beforeTest {
    socRecService.clear()
    socRecService.relationships.value = relationships
  }

  test("onBack calls onExit") {
    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilScreenWithBody<FormBodyModel> {
        onBack?.invoke()
        onExitCalls.awaitItem()
      }
    }
  }

  test("no trusted contacts") {
    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Trusted Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
        mainContentList[0].shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .apply {
            items
              .shouldHaveSize(0)
            footerButton.shouldNotBeNull()
          }
      }
    }
  }

  test("trusted contacts loaded") {
    val testContact =
      EndorsedTrustedContact(
        relationshipId = "test-id",
        trustedContactAlias = TrustedContactAlias("test-contact"),
        keyCertificate = TrustedContactKeyCertificateFake,
        authenticationState = VERIFIED,
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )
    socRecService.relationships.value = SocRecRelationships.EMPTY.copy(
      endorsedTrustedContacts = listOf(
        testContact
      )
    )

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Trusted Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(1)
          .toList()[0]
          .title.shouldBe("test-contact")
      }
    }
  }

  test("invitations loaded") {
    val testInvitation =
      Invitation(
        relationshipId = "test-id",
        trustedContactAlias = TrustedContactAlias("test-invitation"),
        code = "test-token",
        codeBitLength = 40,
        expiresAt = Instant.DISTANT_FUTURE,
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )
    socRecService.relationships.value =
      SocRecRelationships.EMPTY.copy(invitations = listOf(testInvitation))

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Trusted Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(1)
          .toList()[0]
          .title.shouldBe("test-invitation")
      }
    }
  }

  test("start add new trusted contact flow") {
    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .footerButton
          .shouldNotBeNull()
          .onClick()
      }
      onAddTCCalls.awaitItem()
    }
  }
})
