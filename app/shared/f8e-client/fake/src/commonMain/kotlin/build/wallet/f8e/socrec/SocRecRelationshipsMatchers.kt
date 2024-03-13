package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.RecoveryContact
import build.wallet.bitkey.socrec.TrustedContact
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

fun SocRecRelationships.isEmpty() = this == SocRecRelationships.EMPTY

/**
 * Asserts that the [SocRecRelationships] has no relationships.
 */
fun SocRecRelationships.shouldBeEmpty() =
  apply {
    invitations.shouldBeEmpty()
    trustedContacts.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
  }

/**
 * Asserts that the [SocRecRelationships] exactly has the given [contacts] as trusted contacts.
 */
fun SocRecRelationships.shouldHaveEndorsed(vararg contacts: TrustedContact) =
  apply {
    trustedContacts.shouldContainExactlyInAnyOrder(*contacts)
  }

/**
 * Asserts that the [SocRecRelationships] exactly has the given [contacts] and no other relationships.
 */
fun SocRecRelationships.shouldOnlyHaveEndorsed(vararg contacts: TrustedContact) =
  apply {
    shouldHaveEndorsed(*contacts)
    invitations.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
  }

/**
 * Asserts that the [SocRecRelationships] has only a single invitation and no other relationships.
 * Applies the given [block] to the invitation.
 */
fun SocRecRelationships.shouldOnlyHaveSingleInvitation(block: (Invitation) -> Unit) =
  apply {
    invitations.shouldBeSingleton(block)
    trustedContacts.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
  }

/**
 * Asserts that the [SocRecRelationships] has only a single protected customer and no other relationships.
 * Applies the given [block] to the protected customer.
 */
fun SocRecRelationships.shouldOnlyHaveSingleProtectedCustomer(block: (ProtectedCustomer) -> Unit) =
  apply {
    protectedCustomers.shouldBeSingleton(block)
    invitations.shouldBeEmpty()
    trustedContacts.shouldBeEmpty()
    unendorsedTrustedContacts.shouldBeEmpty()
  }

/**
 * Asserts that the [SocRecRelationships] has only a single endorsed trusted contact and no other relationships.
 * Applies the given [block] to the trusted contact.
 */
fun SocRecRelationships.shouldOnlyHaveSingleEndorsedTrustedContact(
  block: (TrustedContact) -> Unit,
) = apply {
  trustedContacts.shouldBeSingleton(block)
  unendorsedTrustedContacts.shouldBeEmpty()
  protectedCustomers.shouldBeEmpty()
  invitations.shouldBeEmpty()
}

fun RecoveryContact.shouldHaveAlias(alias: String) =
  apply {
    trustedContactAlias.alias.shouldBe(alias)
  }

fun ProtectedCustomer.shouldHaveAlias(alias: String) =
  apply {
    this.alias.alias.shouldBe(alias)
  }
