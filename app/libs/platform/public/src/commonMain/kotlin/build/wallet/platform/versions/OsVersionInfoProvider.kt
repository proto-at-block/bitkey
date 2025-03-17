package build.wallet.platform.versions

interface OsVersionInfoProvider {
  fun getOsVersion(): String

  fun getNamedOsVersion(): String
}
