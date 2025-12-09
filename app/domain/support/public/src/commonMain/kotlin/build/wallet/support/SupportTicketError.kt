package build.wallet.support

sealed interface SupportTicketError {
  data object InvalidEmailAddress : SupportTicketError

  data class NetworkFailure(
    val cause: Error,
  ) : SupportTicketError
}
