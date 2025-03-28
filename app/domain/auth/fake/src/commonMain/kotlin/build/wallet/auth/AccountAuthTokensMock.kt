package build.wallet.auth

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.RefreshToken
import kotlinx.datetime.Instant

val AccountAuthTokensMock =
  AccountAuthTokens(
    accessToken = AccessToken("access-token"),
    refreshToken = RefreshToken("refresh-token"),
    accessTokenExpiresAt = Instant.DISTANT_FUTURE
  )

val AccountAuthTokensMock2 =
  AccountAuthTokens(
    accessToken = AccessToken("access-token-2"),
    refreshToken = RefreshToken("refresh-token-2"),
    accessTokenExpiresAt = Instant.DISTANT_FUTURE
  )
