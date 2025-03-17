package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors encountered when creating a new account
 */
@Serializable
enum class CreateAccountClientErrorCode : F8eClientErrorCode {
  /**
   * Indicates that the HW key sent to the server is already linked to another account.
   *
   * Note: If the app key sent to the server is also linked to that account, we won't encounter
   * an error (the account creation will just succeed and return that already existing account).
   * This error surfaces when the app key sent to the server does not match.
   *
   * The customer can recover by going through a recovery.
   */
  HW_AUTH_PUBKEY_IN_USE,

  /**
   * Indicates that the app key sent to the server is already linked to another account.
   *
   * Note: If the HW key sent to the server is also linked to that account, we won't encounter
   * an error (the account creation will just succeed and return that already existing account).
   * This error surfaces when the HW key sent to the server does not match.
   *
   * This error should only happen very rarely, if at all, and the customer will be unable to
   * recover, so we'll clear the locally persisted app key and ask them to try again. The only
   * reason the app key is locally persisted is to allow the account creation endpoint to be
   * idempotent if used with the same app and HW. So the only thing we are preventing by clearing
   * it is the idempotency of the endpoint, so if they then try to use the correct, original HW
   * the account was set up with, they'll run into the above error.
   */
  APP_AUTH_PUBKEY_IN_USE,
}
