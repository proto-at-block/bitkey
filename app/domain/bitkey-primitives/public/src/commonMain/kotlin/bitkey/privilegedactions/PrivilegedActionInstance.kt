package bitkey.privilegedactions

data class PrivilegedActionInstance(
  val id: String,
  val privilegedActionType: PrivilegedActionType,
  val authorizationStrategy: AuthorizationStrategy,
)
