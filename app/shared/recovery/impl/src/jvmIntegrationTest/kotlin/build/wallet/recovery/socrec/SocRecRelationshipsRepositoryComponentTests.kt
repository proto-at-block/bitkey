package build.wallet.recovery.socrec

import app.cash.turbine.test
import build.wallet.testing.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe

class SocRecRelationshipsRepositoryComponentTests : FunSpec({

  val tcName = "Alice (TC)"
  val customerName = "Bob (Protected Customer)"

  test("customer creates TC invite, TC onboards and accepts invite") {
    // Customer onboards and creates Trusted Contact invitation
    val customerApp = launchNewApp(isUsingSocRecFakes = true)
    val customerAccount = customerApp.onboardFullAccountWithFakeHardware()
    val tcInvitation = customerApp.createTcInvite(customerAccount, tcName = tcName)

    // Protected Customer sees pending TC invitation
    customerApp.app.socRecRelationshipsRepository.relationships.test {
      // Discard EMPTY state
      awaitItem()
      awaitItem().run {
        invitations.shouldBeSingleton {
          it.trustedContactAlias.alias.shouldBe(tcName)
          it.token.shouldBe(tcInvitation.token)
          it.recoveryRelationshipId.shouldBe(tcInvitation.recoveryRelationshipId)
        }
        trustedContacts.shouldBeEmpty()
        protectedCustomers.shouldBeEmpty()
      }
    }

    // Trusted Contact onboards by accepting Invitation
    val tcApp = launchNewApp(isUsingSocRecFakes = true)
    tcApp.onboardLiteAccountFromInvitation(tcInvitation, customerName)

    // Trusted Contact sees Protected Customer
    tcApp.app.socRecRelationshipsRepository.relationships.test {
      // Discard EMPTY state
      awaitItem()
      awaitItem().run {
        invitations.shouldBeEmpty()
        trustedContacts.shouldBeEmpty()
        protectedCustomers.shouldBeSingleton {
          it.alias.alias.shouldBe(customerName)
        }
      }
    }

    // TODO(W-5202): enable the rest of the test when real endpoints are used - Customer and TC
    //               apps can't talk to each other right now to successfully sync relationships.
    return@test
    @Suppress("UNREACHABLE_CODE")

    // Protected Customer sees Trusted Contact, no longer pending
    customerApp.app.socRecRelationshipsRepository.relationships.test {
      // Discard EMPTY state
      awaitItem()
      awaitItem().run {
        invitations.shouldBeEmpty()
        trustedContacts.shouldBeSingleton {
          it.trustedContactAlias.alias.shouldBe(tcName)
          it.recoveryRelationshipId.shouldBe(tcInvitation.recoveryRelationshipId)
        }
        protectedCustomers.shouldBeEmpty()
      }
    }
  }
})
