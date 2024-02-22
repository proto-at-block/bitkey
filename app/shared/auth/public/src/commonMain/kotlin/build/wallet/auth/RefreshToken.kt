package build.wallet.auth

import dev.zacsweers.redacted.annotations.Redacted

@Redacted
data class RefreshToken(
  val raw: String,
)
