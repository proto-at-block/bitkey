package build.wallet.platform.versions

actual class OsVersionInfoProviderImpl actual constructor() : OsVersionInfoProvider {
  actual override fun getOsVersion(): String = "N/A"

  actual override fun getNamedOsVersion(): String = "N/A"
}
