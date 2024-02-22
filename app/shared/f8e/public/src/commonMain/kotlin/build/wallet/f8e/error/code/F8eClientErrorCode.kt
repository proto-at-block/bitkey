package build.wallet.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Codes for known request errors for requests sent to F8e.
 *
 * Enum cases should match the strings set by F8e at https://github.com/squareup/wallet/blob/main/server/src/api/errors/src/lib.rs#L52.
 * Specific endpoints should extend [F8eClientErrorCode] to implement their known error codes.
 */
@Serializable
sealed interface F8eClientErrorCode
