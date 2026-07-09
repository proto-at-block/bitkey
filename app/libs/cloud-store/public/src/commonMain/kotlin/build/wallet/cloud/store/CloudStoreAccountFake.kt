package build.wallet.cloud.store

/**
 * Fake implementation of [CloudStoreAccount].
 *
 * Does not represent any real cloud storage. Used for tests and local fake cloud storage.
 *
 * @param identifier Unique identifier for this fake account to identify cloud accounts and
 * backups in tests.
 */
data class CloudStoreAccountFake(
  val identifier: String,
) : CloudStoreAccount {
  companion object {
    const val MOCK_CLOUD_ACCOUNT_ID = "mock-cloud-account"

    val MockCloudAccount = CloudStoreAccountFake(identifier = MOCK_CLOUD_ACCOUNT_ID)
    val CloudStoreAccount1Fake = CloudStoreAccountFake(identifier = "cloud-store-account-1-fake")
    val ProtectedCustomerFake =
      CloudStoreAccountFake(identifier = "cloud-store-protected-customer-fake")
    val TrustedContactFake = CloudStoreAccountFake(identifier = "cloud-store-trusted-contact-fake")

    val cloudStoreAccountFakes = listOf(
      CloudStoreAccount1Fake,
      ProtectedCustomerFake,
      TrustedContactFake
    )
  }
}
