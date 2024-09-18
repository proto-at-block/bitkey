package build.wallet.testing

actual fun getEnvVariable(name: String): String? {
  return System.getenv(name)
}
