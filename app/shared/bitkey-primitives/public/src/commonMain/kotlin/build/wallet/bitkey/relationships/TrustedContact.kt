package build.wallet.bitkey.relationships

/**
 * Common interface for an invitation or trusted contact.
 */
sealed interface TrustedContact {
  val relationshipId: String
  val trustedContactAlias: TrustedContactAlias
  val roles: Set<TrustedContactRole>
}
