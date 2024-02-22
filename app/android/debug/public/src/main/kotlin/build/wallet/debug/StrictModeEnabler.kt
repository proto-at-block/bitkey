package build.wallet.debug

/**
 * Enables strict mode with VM and thread policies to catch common mistakes and problems.
 *
 * https://developer.android.com/reference/kotlin/android/os/StrictMode
 *
 * Only enabled in debug builds.
 */
interface StrictModeEnabler {
  fun configure()
}
