package build.wallet.statemachine.recovery.socrec.list.full

import bitkey.relationships.Relationships
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.FAILED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.UNAUTHENTICATED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.RelationshipsEnrollmentAuthenticationDao
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.*
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationProps
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationUiStateMachine
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactProps
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import com.github.michaelbull.result.Ok
import kotlinx.datetime.Instant
import kotlinx.collections.immutable.persistentSetOf
import okio.ByteString.Companion.encodeUtf8

class ListingTrustedContactsUiStateMachineImplTests : FunSpec({
  val socRecService = SocRecServiceFake()
  val relationshipsEnrollmentAuthenticationDao =
    object : RelationshipsEnrollmentAuthenticationDao {
      var relationshipIdsWithPakeData = persistentSetOf<String>()

      override suspend fun insert(
        recoveryRelationshipId: String,
        protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
        pakeCode: PakeCode,
      ) = Ok(Unit)

      override suspend fun getByRelationshipId(
        recoveryRelationshipId: String,
      ) = Ok(
        if (recoveryRelationshipId in relationshipIdsWithPakeData) {
          RelationshipsEnrollmentAuthenticationDao.RelationshipsEnrollmentAuthenticationRow(
            relationshipId = recoveryRelationshipId,
            protectedCustomerEnrollmentPakeKey = AppKey(
              publicKey = PublicKey("fake-public-key"),
              privateKey = PrivateKey("fake-private-key".encodeUtf8())
            ),
            pakeCode = PakeCode("fake-pake-code".encodeUtf8())
          )
        } else {
          null
        }
      )

      override suspend fun deleteByRelationshipId(recoveryRelationshipId: String) = Ok(Unit)

      override suspend fun clear() = Ok(Unit)
    }

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
      socRecService = socRecService,
      relationshipsEnrollmentAuthenticationDao = relationshipsEnrollmentAuthenticationDao
    )
  val onExitCalls = turbines.create<Unit>("onExit")
  val onAddTCCalls = turbines.create<Unit>("onAddTC")
  val relationships = Relationships.EMPTY
  val props =
    ListingTrustedContactsUiProps(
      account = FullAccountMock,
      onAddTCButtonPressed = { onAddTCCalls.add(Unit) },
      onAcceptTrustedContactInvite = {},
      onExit = { onExitCalls.add(Unit) }
    )

  beforeTest {
    socRecService.reset()
    socRecService.socRecRelationships.value = relationships
    relationshipsEnrollmentAuthenticationDao.relationshipIdsWithPakeData = persistentSetOf()
  }

  test("onBack calls onExit") {
    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        onBack?.invoke()
        onExitCalls.awaitItem()
      }
    }
  }

  test("no trusted contacts") {
    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        formScreenTitle?.title.shouldBe("Recovery Contacts")
        header?.headline.shouldBeNull()
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
        mainContentList[0].shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .apply {
            items
              .shouldHaveSize(1)
              .single()
              .title
              .shouldBe("Add new contact")
            footerButton.shouldBeNull()
          }
      }
    }
  }

  test("trusted contacts loaded") {
    val testContact =
      EndorsedTrustedContact(
        id = RelationshipId("test-id"),
        trustedContactAlias = TrustedContactAlias("test-contact"),
        keyCertificate = TrustedContactKeyCertificateFake,
        authenticationState = VERIFIED,
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )
    socRecService.socRecRelationships.value = Relationships.EMPTY.copy(
      endorsedTrustedContacts = listOf(
        testContact
      )
    )

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        formScreenTitle?.title.shouldBe("Recovery Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(2)
          .toList()
          .apply {
            this[0].title.shouldBe("test-contact")
            this[1].title.shouldBe("Add new contact")
          }
      }
    }
  }

  test("invitations loaded") {
    val testInvitation =
      Invitation(
        id = RelationshipId("test-id"),
        trustedContactAlias = TrustedContactAlias("test-invitation"),
        code = "test-token",
        codeBitLength = 40,
        expiresAt = Instant.DISTANT_FUTURE,
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )
    socRecService.socRecRelationships.value =
      Relationships.EMPTY.copy(invitations = listOf(testInvitation))

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        formScreenTitle?.title.shouldBe("Recovery Contacts")
        mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(2)
          .toList()
          .apply {
            this[0].title.shouldBe("test-invitation")
            this[1].title.shouldBe("Add new contact")
          }
      }
    }
  }

  test("failed unendorsed trusted contacts loaded") {
    val failedContact = UnendorsedTrustedContactFake.copy(
      authenticationState = FAILED
    )
    socRecService.socRecRelationships.value = Relationships.EMPTY.copy(
      unendorsedTrustedContacts = listOf(failedContact)
    )

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        formScreenTitle?.title.shouldBe("Recovery Contacts")
        mainContentList.shouldHaveSize(2)
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(2)
          .toList()[0]
          .apply {
            title.shouldBe("someContact")
            secondaryText.shouldBe("Failed")
            onClick.shouldNotBeNull().invoke()
          }
      }

      awaitBodyMock<ViewingRecoveryContactProps>("viewing-recovery-contact") {
        recoveryContact.shouldBe(failedContact)
        account.shouldBe(FullAccountMock)
      }
    }
  }

  test("unauthenticated unendorsed trusted contacts stay visible") {
    val unendorsedContact = UnendorsedTrustedContactFake.copy(
      authenticationState = UNAUTHENTICATED
    )
    relationshipsEnrollmentAuthenticationDao.relationshipIdsWithPakeData =
      persistentSetOf(unendorsedContact.id.value)
    socRecService.socRecRelationships.value = Relationships.EMPTY.copy(
      unendorsedTrustedContacts = listOf(unendorsedContact)
    )

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        mainContentList.shouldHaveSize(2)
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(2)
          .toList()[0]
          .apply {
            title.shouldBe("someContact")
            secondaryText.shouldBe("Pending")
            onClick.shouldBeNull()
          }
      }
    }
  }

  test("unauthenticated unendorsed trusted contacts without pake data show failed") {
    val unendorsedContact = UnendorsedTrustedContactFake.copy(
      authenticationState = UNAUTHENTICATED
    )
    socRecService.socRecRelationships.value = Relationships.EMPTY.copy(
      unendorsedTrustedContacts = listOf(unendorsedContact)
    )

    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel>(
        matching = {
          it.mainContentList.first()
            .shouldBeInstanceOf<ListGroup>()
            .listGroupModel
            .items
            .toList()[0]
            .secondaryText == "Failed"
        }
      ) {
        mainContentList.shouldHaveSize(2)
          .first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .shouldHaveSize(2)
          .toList()[0]
          .apply {
            title.shouldBe("someContact")
            secondaryText.shouldBe("Failed")
            onClick.shouldNotBeNull().invoke()
          }
      }

      awaitBodyMock<ViewingRecoveryContactProps>("viewing-recovery-contact") {
        recoveryContact.shouldBe(unendorsedContact)
        account.shouldBe(FullAccountMock)
        unendorsedContactRelationshipIdsMissingPakeData.shouldBe(
          persistentSetOf(unendorsedContact.id.value)
        )
      }
    }
  }

  test("start add new trusted contact flow") {
    listingTrustedContactsUiStateMachine.test(props) {
      awaitUntilBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items
          .last()
          .apply {
            title.shouldBe("Add new contact")
          }
          .onClick
          .shouldNotBeNull()
          .invoke()
      }
      onAddTCCalls.awaitItem()
    }
  }
})
