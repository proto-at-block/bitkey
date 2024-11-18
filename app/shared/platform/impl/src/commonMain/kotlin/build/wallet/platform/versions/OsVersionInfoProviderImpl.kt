package build.wallet.platform.versions

expect class OsVersionInfoProviderImpl() : OsVersionInfoProvider {
  override fun getOsVersion(): String

  override fun getNamedOsVersion(): String
}
