package build.wallet.auth

val AccountAuthTokensMock =
  AccountAuthTokens(
    accessToken = AccessToken("access-token"),
    refreshToken = RefreshToken("refresh-token")
  )

val AccountAuthTokensMock2 =
  AccountAuthTokens(
    accessToken = AccessToken("access-token-2"),
    refreshToken = RefreshToken("refresh-token-2")
  )
