package build.wallet.cloud.store

/**
 * Normalizes cloud store routing when local fake cloud storage is active.
 *
 * Fake stores are account-scoped and only accept [CloudStoreAccountFake]. If fake cloud mode is
 * enabled while a stale real provider account is still flowing through onboarding or recovery,
 * route that operation through the stable mock fake account instead.
 */
data class CloudStoreAccountRouting(
  val account: CloudStoreAccount,
  val useFakeStore: Boolean,
)

fun CloudStoreAccount.cloudStoreAccountRouting(
  isFakeCloudStoreActive: Boolean,
): CloudStoreAccountRouting =
  when {
    this is CloudStoreAccountFake ->
      CloudStoreAccountRouting(account = this, useFakeStore = true)
    isFakeCloudStoreActive ->
      CloudStoreAccountRouting(
        account = CloudStoreAccountFake.MockCloudAccount,
        useFakeStore = true
      )
    else ->
      CloudStoreAccountRouting(account = this, useFakeStore = false)
  }
