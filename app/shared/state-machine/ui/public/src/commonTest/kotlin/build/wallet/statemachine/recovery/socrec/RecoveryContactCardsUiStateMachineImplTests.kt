package build.wallet.statemachine.recovery.socrec

import app.cash.turbine.plusAssign
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.bitkey.socrec.UnendorsedTrustedContactFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.recovery.socrec.SocRecServiceMock
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
  val socRecService = SocRecServiceMock(turbines::create)

  val recoveryContactCardsUiStateMachine =
    RecoveryContactCardsUiStateMachineImpl(
      clock = ClockFake(),
      socRecService = socRecService
    )
  val onClickCalls = turbines.create<Unit>("onClick call")
  val relationships = SocRecRelationships.EMPTY
  val recoveryContactCardsUiProps =
    RecoveryContactCardsUiProps(
      onClick = { onClickCalls += Unit }
    )

  beforeTest {
    socRecService.clear()
    socRecService.relationships.value = relationships
  }

  test("no invitations produces no cards") {
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeEmpty()
    }
  }

  test("1 invitation produces 1 card") {
    socRecService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake)
    )
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
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
    socRecService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_FUTURE))
    )
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Pending")
        }
      }
    }
  }

  test("Expired invitation marked as expired") {
    socRecService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_PAST))
    )

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().let {
        it.size.shouldBeEqual(1)
        it.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Expired")
        }
      }
    }
  }

  test("multiple invitations produces multiple cards") {
    socRecService.relationships.value = relationships.copy(
      invitations =
        listOf(
          InvitationFake,
          InvitationFake
        )
    )

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().size.shouldBeEqual(2)
    }
  }

  test("Failed Unendorsed contact produces a card") {
    socRecService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.FAILED
        )
      )
    )
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
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
    socRecService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
        )
      )
    )

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
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
    socRecService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.TAMPERED
        )
      )
    )

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
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
    socRecService.relationships.value = relationships.copy(
      unendorsedTrustedContacts = listOf(
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED
        ),
        UnendorsedTrustedContactFake.copy(
          authenticationState = TrustedContactAuthenticationState.VERIFIED
        )
      )
    )

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeEmpty()
    }
  }
})
