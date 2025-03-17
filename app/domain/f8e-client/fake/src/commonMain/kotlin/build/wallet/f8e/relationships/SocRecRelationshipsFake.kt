package build.wallet.f8e.relationships

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake2
import build.wallet.compose.collections.immutableListOf

val RelationshipsFake =
  Relationships(
    invitations = listOf(),
    endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1, EndorsedTrustedContactFake2, EndorsedBeneficiaryFake),
    unendorsedTrustedContacts = listOf(),
    protectedCustomers = immutableListOf()
  )
