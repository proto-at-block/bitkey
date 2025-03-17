package build.wallet.cloud.store

/**
 * @param ubiquityIdentityToken The iCloud ubiquity identity token:
 *        https://developer.apple.com/documentation/foundation/filemanager/1408036-ubiquityidentitytoken
 */
@Suppress("ClassName")
data class iCloudAccount(val ubiquityIdentityToken: String) : CloudStoreAccount
