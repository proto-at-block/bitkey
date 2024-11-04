package build.wallet.bitkey.relationships

/**
 * Common interface for an invitation or trusted contact.
 */
sealed interface TrustedContact {
  @Deprecated("Use typed ID", ReplaceWith("id"))
  val relationshipId: String
  val id: RelationshipId get() = RelationshipId(relationshipId)
  val trustedContactAlias: TrustedContactAlias
  val roles: Set<TrustedContactRole>
}
