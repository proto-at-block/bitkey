package build.wallet.inheritance

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import kotlinx.coroutines.flow.MutableSharedFlow

class InheritanceRelationshipsProviderFake(
  var endorsedInheritanceContactsResult: List<EndorsedTrustedContact>? = null,
) : InheritanceRelationshipsProvider {
  override val endorsedInheritanceContacts = MutableSharedFlow<List<EndorsedTrustedContact>>()

  override suspend fun getEndorsedInheritanceContacts(): List<EndorsedTrustedContact>? {
    return endorsedInheritanceContactsResult
  }
}
