package build.wallet.recovery.socrec

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.relationships.shouldHaveAlias
import build.wallet.f8e.relationships.shouldOnlyHaveSingleEndorsedTrustedContact
import build.wallet.f8e.relationships.shouldOnlyHaveSingleInvitation
import build.wallet.f8e.relationships.shouldOnlyHaveSingleProtectedCustomer
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SocRecServiceFunctionalTests : FunSpec({
  val tcName = "Alice (TC)"
  val customerName = "Bob (Protected Customer)"

  test("customer creates TC invite, TC onboards and accepts invite") {
    // Customer onboards and creates Trusted Contact invitation
    val customerApp = launchNewApp()
    customerApp.onboardFullAccountWithFakeHardware()
    val (inviteCode, tcInvitation) = customerApp.createTcInvite(tcName = tcName)

    // Protected Customer sees pending TC invitation
    customerApp.socRecService.socRecRelationships.test {
      awaitUntil { it != null && it.invitations.isNotEmpty() }.run {
        shouldNotBeNull()
        shouldOnlyHaveSingleInvitation {
          it.shouldHaveAlias(tcName)
          it.relationshipId.shouldBe(tcInvitation.relationshipId)
        }
      }
    }

    // Trusted Contact onboards by accepting Invitation
    val tcApp = launchNewApp()
    tcApp.onboardLiteAccountFromInvitation(inviteCode, customerName)

    // Trusted Contact sees Protected Customer
    tcApp.socRecService.socRecRelationships.test {
      awaitUntil { it != null && it.protectedCustomers.isNotEmpty() }.run {
        shouldNotBeNull()
        shouldOnlyHaveSingleProtectedCustomer {
          it.shouldHaveAlias(customerName)
        }
      }
    }

    // TODO(W-5202): enable the rest of the test when real endpoints are used - Customer and TC
    //               apps can't talk to each other right now to successfully sync relationships.
    return@test
    @Suppress("UNREACHABLE_CODE")

    // Protected Customer sees Trusted Contact, no longer pending
    customerApp.socRecService.socRecRelationships.test {
      awaitUntil { it != null && it.endorsedTrustedContacts.isNotEmpty() }.run {
        shouldNotBeNull()
        shouldOnlyHaveSingleEndorsedTrustedContact {
          it.shouldHaveAlias(tcName)
          it.relationshipId.shouldBe(tcInvitation.relationshipId)
        }
      }
    }
  }
})
