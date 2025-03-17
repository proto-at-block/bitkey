package bitkey.relationships

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.compose.collections.immutableListOf
import kotlinx.collections.immutable.ImmutableList

data class Relationships(
  val invitations: List<Invitation>,
  val endorsedTrustedContacts: List<EndorsedTrustedContact>,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact>,
) {
  companion object {
    val EMPTY =
      Relationships(
        invitations = emptyList(),
        endorsedTrustedContacts = emptyList(),
        protectedCustomers = immutableListOf(),
        unendorsedTrustedContacts = emptyList()
      )
  }
}
