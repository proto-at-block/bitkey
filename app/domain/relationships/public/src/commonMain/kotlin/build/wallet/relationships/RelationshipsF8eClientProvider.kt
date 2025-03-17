package build.wallet.relationships

import build.wallet.f8e.relationships.RelationshipsF8eClient

/**
 * Provides a [RelationshipsF8eClient] instance based on whether the account is configured to use
 * fakes.
 */
fun interface RelationshipsF8eClientProvider {
  fun get(): RelationshipsF8eClient
}
