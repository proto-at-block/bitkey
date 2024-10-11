package build.wallet.inheritance

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.coroutines.scopes.filterEach
import build.wallet.relationships.RelationshipsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Adapts the Relationships service's relationship data to to the inheritance
 * data provider interface.
 */
class InheritanceRelationshipsAdapter(
  private val relationshipsService: RelationshipsService,
) : InheritanceRelationshipsProvider {
  override val endorsedInheritanceContacts: Flow<List<EndorsedTrustedContact>> = relationshipsService.relationships
    .filterNotNull()
    .map { it.endorsedTrustedContacts }
    .filterEach { TrustedContactRole.Beneficiary in it.roles }

  override suspend fun getEndorsedInheritanceContacts(): List<EndorsedTrustedContact>? {
    return relationshipsService.relationships
      .value
      ?.endorsedTrustedContacts
      ?.filter { TrustedContactRole.Beneficiary in it.roles }
  }
}
