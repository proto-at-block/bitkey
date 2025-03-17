package build.wallet.onboarding

import bitkey.f8e.error.code.CreateAccountClientErrorCode.APP_AUTH_PUBKEY_IN_USE
import bitkey.f8e.error.code.CreateAccountClientErrorCode.HW_AUTH_PUBKEY_IN_USE

data class ErrorStoringSealedCsekError(
  override val cause: Throwable,
) : Error(cause)

/**
 * Corresponds to [HW_AUTH_PUBKEY_IN_USE].
 */
data class HardwareKeyAlreadyInUseError(
  override val cause: Throwable,
) : Error(cause)

/**
 * Corresponds to [APP_AUTH_PUBKEY_IN_USE].
 */
data object AppKeyAlreadyInUseError : Error()
