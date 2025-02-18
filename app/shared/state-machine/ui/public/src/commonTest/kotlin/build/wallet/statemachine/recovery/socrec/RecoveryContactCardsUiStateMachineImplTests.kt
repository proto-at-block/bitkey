package build.wallet.statemachine.recovery.socrec

import app.cash.turbine.plusAssign
import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.relationships.UnendorsedTrustedContactFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.relationships.Relationships
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE
import kotlinx.datetime.Instant.Companion.DISTANT_PAST

class RecoveryContactCardsUiStateMachineImplTests : FunSpec({
  val relationshipsService = RelationshipsServiceMock(
    turbine = turbines::create
  )

  val recoveryContactCardsUiStateMachine =
    RecoveryContactCardsUiStateMachineImpl(
      clock = ClockFake(),
      relationshipsService = relationshipsService
    )
  val onClickCalls = turbines.create<Unit>("onClick call")
  val relationships = Relationships.EMPTY
  val recoveryContactCardsUiProps =
    RecoveryContactCardsUiProps(
      onClick = { onClickCalls += Unit }
    )

  beforeTest {
    relationshipsService.clear()
    relationshipsService.relationships.value = relationships
  }

  test("no invitations produces no cards") {
    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().shouldBeEmpty()
    }
  }

  test("1 invitation produces 1 card") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake)
    )
    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("trustedContactAlias fake")
          cardModel.onClick.shouldNotBeNull().invoke()
        }
        onClickCalls.awaitItem()
      }
    }
  }

  test("Pending invitation marked as pending") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_FUTURE))
    )
    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Pending")
        }
      }
    }
  }

  test("Expired invitation marked as expired") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_PAST))
    )

    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Expired")
        }
      }
    }
  }

  test("multiple invitations produces multiple cards") {
    relationshipsService.relationships.value = relationships.copy(
      invitations =
        listOf(
          InvitationFake,
          InvitationFake
        )
    )

    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().size.shouldBeEqual(2)
    }
  }

  test("Failed Unendorsed contact produces a card") {
    relationshipsService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.FAILED
        )
      )
    )
    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("someContact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Failed")
        }
      }
    }
  }

  test("Pake unavailability Unendorsed contact produces a card") {
    relationshipsService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
        )
      )
    )

    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("someContact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Failed")
        }
      }
    }
  }

  test("Tampered Unendorsed contact produces a card") {
    relationshipsService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.TAMPERED
        )
      )
    )

    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("someContact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Invalid")
        }
      }
    }
  }

  test("Only failed Unendorsed contact produces a card") {
    relationshipsService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED
        ),
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.VERIFIED
        )
      )
    )

    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().shouldBeEmpty()
    }
  }

  test("Only show Recovery TC invite cards") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(
        BeneficiaryInvitationFake.copy(expiresAt = DISTANT_FUTURE),
        BeneficiaryInvitationFake.copy(expiresAt = DISTANT_PAST)
      )
    )
    recoveryContactCardsUiStateMachine.testWithVirtualTime(recoveryContactCardsUiProps) {
      awaitItem().size.shouldBeEqual(0)
    }
  }
})
