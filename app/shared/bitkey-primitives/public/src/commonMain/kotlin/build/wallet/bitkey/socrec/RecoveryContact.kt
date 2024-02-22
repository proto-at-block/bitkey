package build.wallet.bitkey.socrec

/**
 * Common interface for an invitation or trusted contact.
 */
sealed interface RecoveryContact {
  val recoveryRelationshipId: String
  val trustedContactAlias: TrustedContactAlias
}
