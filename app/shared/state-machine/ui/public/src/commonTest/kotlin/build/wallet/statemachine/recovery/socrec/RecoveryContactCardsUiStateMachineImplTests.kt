package build.wallet.statemachine.recovery.socrec

import app.cash.turbine.plusAssign
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.bitkey.socrec.UnendorsedTrustedContactFake
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.statemachine.core.test
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE
import kotlinx.datetime.Instant.Companion.DISTANT_PAST

class RecoveryContactCardsUiStateMachineImplTests : FunSpec({

  val recoveryContactCardsUiStateMachine =
    RecoveryContactCardsUiStateMachineImpl(
      clock = ClockFake()
    )
  val onClickCalls = turbines.create<Unit>("onClick call")
  val relationships = SocRecRelationships(
    invitations = listOf(),
    unendorsedTrustedContacts = emptyList(),
    endorsedTrustedContacts = emptyList(),
    protectedCustomers = emptyImmutableList()
  )
  val recoveryContactCardsUiProps =
    RecoveryContactCardsUiProps(
      relationships = relationships,
      onClick = { onClickCalls += Unit }
    )

  test("no invitations produces no cards") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(relationships = SocRecRelationships.EMPTY)
    ) {
      awaitItem().shouldBeEmpty()
    }
  }

  test("1 invitation produces 1 card") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          invitations = listOf(InvitationFake)
        )
      )
    ) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.title.string.shouldBeEqual("trustedContactAlias fake")
          cardModel.onClick.shouldNotBeNull().invoke()
        }
        onClickCalls.awaitItem()
      }
    }
  }

  test("Pending invitation marked as pending") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_FUTURE))
        )
      )
    ) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Pending")
        }
      }
    }
  }

  test("Expired invitation marked as expired") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_PAST))
        )
      )
    ) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Expired")
        }
      }
    }
  }

  test("multiple invitations produces multiple cards") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          invitations =
            listOf(
              InvitationFake,
              InvitationFake
            )
        )
      )
    ) {
      awaitItem().size.shouldBeEqual(2)
    }
  }

  test("Failed Unendorsed contact produces a card") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          unendorsedTrustedContacts = listOf(
            UnendorsedTrustedContactFake.copy(
              authenticationState = TrustedContactAuthenticationState.FAILED
            )
          )
        )
      )
    ) {
      awaitItem().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.string.shouldBeEqual("someContact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Failed")
        }
      }
    }
  }

  test("Pake unavailability Unendorsed contact produces a card") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          unendorsedTrustedContacts = listOf(
            UnendorsedTrustedContactFake.copy(
              authenticationState = TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
            )
          )
        )
      )
    ) {
      awaitItem().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.string.shouldBeEqual("someContact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Failed")
        }
      }
    }
  }

  test("Tampered Unendorsed contact produces a card") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          unendorsedTrustedContacts = listOf(
            UnendorsedTrustedContactFake.copy(
              authenticationState = TrustedContactAuthenticationState.TAMPERED
            )
          )
        )
      )
    ) {
      awaitItem().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.string.shouldBeEqual("someContact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Invalid")
        }
      }
    }
  }

  test("Only failed Unendorsed contact produces a card") {
    recoveryContactCardsUiStateMachine.test(
      recoveryContactCardsUiProps.copy(
        relationships = relationships.copy(
          unendorsedTrustedContacts = listOf(
            UnendorsedTrustedContactFake.copy(
              authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED
            ),
            UnendorsedTrustedContactFake.copy(
              authenticationState = TrustedContactAuthenticationState.VERIFIED
            )
          )
        )
      )
    ) {
      awaitItem().shouldBeEmpty()
    }
  }
})
