package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.EndorsedTrustedContactFake1
import build.wallet.bitkey.socrec.TrustedContactFake2
import build.wallet.compose.collections.immutableListOf

val SocRecRelationshipsFake =
  SocRecRelationships(
    invitations = listOf(),
    endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1, TrustedContactFake2),
    unendorsedTrustedContacts = listOf(),
    protectedCustomers = immutableListOf()
  )
