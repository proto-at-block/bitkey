package build.wallet.f8e.relationships

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.TrustedContact
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

fun Relationships.isEmpty() = this == Relationships.EMPTY

/**
 * Asserts that the [Relationships] has no relationships.
 */
fun Relationships.shouldBeEmpty() =
  apply {
    invitations.shouldBeEmpty()
    endorsedTrustedContacts.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
  }

/**
 * Asserts that the [Relationships] exactly has the given [contacts] as trusted contacts.
 */
fun Relationships.shouldHaveEndorsed(vararg contacts: EndorsedTrustedContact) =
  apply {
    endorsedTrustedContacts.shouldContainExactlyInAnyOrder(*contacts)
  }

/**
 * Asserts that the [Relationships] exactly has the given [contacts] and no other relationships.
 */
fun Relationships.shouldOnlyHaveEndorsed(vararg contacts: EndorsedTrustedContact) =
  apply {
    shouldHaveEndorsed(*contacts)
    invitations.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
  }

/**
 * Asserts that the [Relationships] has only a single invitation and no other relationships.
 * Applies the given [block] to the invitation.
 */
fun Relationships.shouldOnlyHaveSingleInvitation(block: (Invitation) -> Unit) =
  apply {
    invitations.shouldBeSingleton(block)
    endorsedTrustedContacts.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
  }

/**
 * Asserts that the [Relationships] has only a single protected customer and no other relationships.
 * Applies the given [block] to the protected customer.
 */
fun Relationships.shouldOnlyHaveSingleProtectedCustomer(block: (ProtectedCustomer) -> Unit) =
  apply {
    protectedCustomers.shouldBeSingleton(block)
    invitations.shouldBeEmpty()
    endorsedTrustedContacts.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
  }

/**
 * Asserts that the [Relationships] has only a single endorsed trusted contact and no other relationships.
 * Applies the given [block] to the trusted contact.
 */
fun Relationships.shouldOnlyHaveSingleEndorsedTrustedContact(
  block: (EndorsedTrustedContact) -> Unit,
) = apply {
  endorsedTrustedContacts.shouldBeSingleton(block)
  unendorsedTrustedContacts.shouldBeEmpty()
  protectedCustomers.shouldBeEmpty()
  invitations.shouldBeEmpty()
}

fun TrustedContact.shouldHaveAlias(alias: String) =
  apply {
    trustedContactAlias.alias.shouldBe(alias)
  }

fun ProtectedCustomer.shouldHaveAlias(alias: String) =
  apply {
    this.alias.alias.shouldBe(alias)
  }
