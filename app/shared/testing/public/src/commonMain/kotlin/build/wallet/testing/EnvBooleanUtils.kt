package build.wallet.testing

fun getEnvBoolean(name: String): Boolean? {
  return getEnvVariable(name)?.toBoolean()
}
