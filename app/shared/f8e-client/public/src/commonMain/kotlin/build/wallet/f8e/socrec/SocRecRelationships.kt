package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.EndorsedTrustedContact
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.compose.collections.immutableListOf
import kotlinx.collections.immutable.ImmutableList

data class SocRecRelationships(
  val invitations: List<Invitation>,
  val endorsedTrustedContacts: List<EndorsedTrustedContact>,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact>,
) {
  companion object {
    val EMPTY =
      SocRecRelationships(
        invitations = emptyList(),
        endorsedTrustedContacts = emptyList(),
        protectedCustomers = immutableListOf(),
        unendorsedTrustedContacts = emptyList()
      )
  }
}
