package build.wallet.platform.system

actual fun exitProcess(status: Int): Nothing {
  kotlin.system.exitProcess(status)
}
