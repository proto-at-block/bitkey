package build.wallet.statemachine.recovery.socrec

import app.cash.turbine.plusAssign
import bitkey.relationships.Relationships
import build.wallet.LoadableValue.InitialLoading
import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.relationships.UnendorsedTrustedContactFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.core.test
import build.wallet.testing.shouldBeLoaded
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.should
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE
import kotlinx.datetime.Instant.Companion.DISTANT_PAST

class RecoveryContactCardsUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val relationshipsService = RelationshipsServiceMock(
    turbine = turbines::create,
    clock = clock
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
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().shouldBeEmpty()
    }
  }

  test("waits for relationships cache to load before loading") {
    relationshipsService.relationships.value = null

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBe(InitialLoading)

      relationshipsService.relationships.value = relationships

      awaitItem().shouldBeLoaded().shouldBeEmpty()
    }
  }

  test("1 invitation produces 1 card") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake)
    )
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().let { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
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
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().let { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Pending")
          cardModel.style.shouldBeEqual(
            build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient(
              backgroundColor =
                build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient.BackgroundColor.InverseBackground
            )
          )
        }
      }
    }
  }

  test("Expired invitation marked as expired") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(InvitationFake.copy(expiresAt = DISTANT_PAST))
    )

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().let { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Expired")
          cardModel.style.shouldBeEqual(
            build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient(
              backgroundColor =
                build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient.BackgroundColor.Default
            )
          )
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

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().size.shouldBeEqual(2)
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
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("someContact")
          cardModel.subtitle.shouldNotBeNull().shouldBeEqual("Failed Recovery Contact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Review")
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

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("someContact")
          cardModel.subtitle.shouldNotBeNull().shouldBeEqual("Failed Recovery Contact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Review")
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

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().should { cards ->
        cards.size.shouldBeEqual(1)
        cards.first().let { cardModel ->
          cardModel.title.shouldNotBeNull().string.shouldBeEqual("someContact")
          cardModel.subtitle.shouldNotBeNull().shouldBeEqual("Invalid Recovery Contact")
          cardModel.trailingButton.shouldNotBeNull().text.shouldBeEqual("Review")
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

    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().shouldBeEmpty()
    }
  }

  test("Only show Recovery TC invite cards") {
    relationshipsService.relationships.value = relationships.copy(
      invitations = listOf(
        BeneficiaryInvitationFake.copy(expiresAt = DISTANT_FUTURE),
        BeneficiaryInvitationFake.copy(expiresAt = DISTANT_PAST)
      )
    )
    recoveryContactCardsUiStateMachine.test(recoveryContactCardsUiProps) {
      awaitItem().shouldBeLoaded().size.shouldBeEqual(0)
    }
  }
})
