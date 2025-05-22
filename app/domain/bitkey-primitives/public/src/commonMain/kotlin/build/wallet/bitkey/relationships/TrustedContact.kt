package build.wallet.bitkey.relationships

/**
 * Common interface for an invitation or Recovery Contact.
 */
sealed interface TrustedContact : RecoveryEntity {
  @Deprecated("Use typed ID", ReplaceWith("id"))
  override val relationshipId: String
  val trustedContactAlias: TrustedContactAlias
  val roles: Set<TrustedContactRole>
}
