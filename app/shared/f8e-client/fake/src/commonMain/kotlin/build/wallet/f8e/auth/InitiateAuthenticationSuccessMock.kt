package build.wallet.f8e.auth

import build.wallet.f8e.auth.AuthenticationService.InitiateAuthenticationSuccess

val InitiateAuthenticationSuccessMock = InitiateAuthenticationSuccess(
  username = "",
  accountId = "auth-challenge-mock-account-id",
  challenge = "auth-challenge-challenge",
  session = "auth-challenge-session"
)
