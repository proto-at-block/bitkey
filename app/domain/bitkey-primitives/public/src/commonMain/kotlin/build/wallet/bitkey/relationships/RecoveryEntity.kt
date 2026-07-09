package build.wallet.bitkey.relationships

sealed interface RecoveryEntity {
  val id: RelationshipId
  val recoveryAlias: String get() = when (this) {
    is ProtectedCustomer -> alias.alias
    is TrustedContact -> trustedContactAlias.alias
    is Invitation -> trustedContactAlias.alias
  }
}
