package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.compose.collections.immutableListOf
import kotlinx.collections.immutable.ImmutableList

data class SocRecRelationships(
  val invitations: List<Invitation>,
  val trustedContacts: List<TrustedContact>,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  // TODO(BKR-853) Make non-optional
  val unendorsedTrustedContacts: List<UnendorsedTrustedContact> = emptyList(),
) {
  companion object {
    val EMPTY =
      SocRecRelationships(
        invitations = emptyList(),
        trustedContacts = emptyList(),
        protectedCustomers = immutableListOf(),
        unendorsedTrustedContacts = emptyList()
      )
  }
}
