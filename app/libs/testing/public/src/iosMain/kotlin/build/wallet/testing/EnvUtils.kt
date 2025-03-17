package build.wallet.testing

import platform.Foundation.NSProcessInfo

actual fun getEnvVariable(name: String): String? {
  return NSProcessInfo.processInfo.environment[name] as? String
}
