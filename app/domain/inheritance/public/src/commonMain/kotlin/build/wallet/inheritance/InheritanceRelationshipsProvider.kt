package build.wallet.inheritance

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the Recovery Contacts related to inheritance.
 */
interface InheritanceRelationshipsProvider {
  /**
   * The current saved list of endorsed inheritance contacts.
   */
  val endorsedInheritanceContacts: Flow<List<EndorsedTrustedContact>>

  /**
   * Get the most recent list of inheritance contacts.
   */
  suspend fun getEndorsedInheritanceContacts(): List<EndorsedTrustedContact>?
}
