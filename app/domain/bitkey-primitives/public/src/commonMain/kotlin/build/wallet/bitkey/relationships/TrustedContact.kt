package build.wallet.bitkey.relationships

/**
 * Common interface for an invitation or trusted contact.
 */
sealed interface TrustedContact : RecoveryEntity {
  val trustedContactAlias: TrustedContactAlias
  val roles: Set<TrustedContactRole>
}
