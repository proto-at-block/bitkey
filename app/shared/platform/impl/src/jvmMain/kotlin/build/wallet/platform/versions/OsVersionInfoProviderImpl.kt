package build.wallet.platform.versions

actual class OsVersionInfoProviderImpl actual constructor() : OsVersionInfoProvider {
  override fun getOsVersion(): String = "N/A"

  override fun getNamedOsVersion(): String = "N/A"
}
