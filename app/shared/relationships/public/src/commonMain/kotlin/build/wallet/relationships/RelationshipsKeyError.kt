package build.wallet.relationships

sealed class RelationshipsKeyError(
  cause: Throwable? = null,
  message: String? = null,
) : Error(message, cause) {
  /**
   * Error sent when there was an issue generating the keypair
   */
  class UnableToGenerateKey(
    cause: Throwable? = null,
    message: String? = null,
  ) : RelationshipsKeyError(cause, message)

  /**
   * Error sent when there was an issue persisting the keypair to the database or keystore
   */
  class UnableToPersistKey(
    cause: Throwable? = null,
    message: String? = null,
  ) : RelationshipsKeyError(cause, message)

  /**
   * Error sent when there was an issue retrieving the key from the datastore
   */
  class UnableToRetrieveKey(
    cause: Throwable? = null,
    message: String? = null,
  ) : RelationshipsKeyError(cause, message)

  /**
   * Error sent when there is no key in the data store to retrieve
   */
  class NoKeyAvailable(
    cause: Throwable? = null,
    message: String? = null,
  ) : RelationshipsKeyError(cause, message)

  /**
   * Error sent when there is no private key available though the public key is present
   */
  class NoPrivateKeyAvailable(
    cause: Throwable? = null,
    message: String? = null,
  ) : RelationshipsKeyError(cause, message)
}
