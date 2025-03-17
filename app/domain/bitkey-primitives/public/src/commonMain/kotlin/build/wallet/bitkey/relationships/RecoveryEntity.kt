package build.wallet.bitkey.relationships

sealed interface RecoveryEntity {
  val relationshipId: String
  val id: RelationshipId get() = RelationshipId(relationshipId)
  val recoveryAlias: String get() = when (this) {
    is ProtectedCustomer -> alias.alias
    is TrustedContact -> trustedContactAlias.alias
    is Invitation -> trustedContactAlias.alias
  }
}
