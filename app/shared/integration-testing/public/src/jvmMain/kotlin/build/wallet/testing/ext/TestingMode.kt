package build.wallet.testing.ext

import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.tags.TestTag.FlakyTest
import build.wallet.testing.tags.TestTag.IsolatedTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope

/*
 * Helper to run the same test in both legacy and private wallet modes.
 */
fun FunSpec.testForLegacyAndPrivateWallet(
  name: String,
  isIsolatedTest: Boolean = false,
  isFlakyTest: Boolean = false,
  block: suspend TestScope.(app: AppTester) -> Unit,
) {
  val tags = setOf(IsolatedTest).takeIf { isIsolatedTest }.orEmpty()
    .union(setOf(FlakyTest).takeIf { isFlakyTest }.orEmpty())

  test("$name [legacy] ")
    .config(tags = tags) {
      this.block(launchNewApp())
    }

  test("$name [private]")
    .config(tags = tags) {
      this.block(launchPrivateWalletApp())
    }
}

/*
 * Helper to run the same test with two apps in all combinations of legacy and private wallet modes.
 *
 * Private -> Legacy is notably not included here, because the only use case for that combination
 * is in inheritance. We leave that one as an exercise for inheritance-specific tests.
 */
fun FunSpec.testWithTwoApps(
  name: String,
  app1Factory: suspend TestScope.(mode: AppMode) -> AppTester = { launchAppForMode(it) },
  app2Factory: suspend TestScope.(
    app1: AppTester,
    mode: AppMode,
  ) -> AppTester = { _, mode -> launchAppForMode(mode) },
  block: suspend TestScope.(AppTester, AppTester) -> Unit,
) {
  test(" $name [legacy -> legacy]") {
    val app1 = app1Factory(AppMode.Legacy)
    val app2 = app2Factory(app1, AppMode.Legacy)
    this.block(app1, app2)
  }
  test(" $name [legacy -> private]") {
    val app1 = app1Factory(AppMode.Legacy)
    val app2 = app2Factory(app1, AppMode.Private)
    this.block(app1, app2)
  }
  test("$name [private -> private]") {
    val app1 = app1Factory(AppMode.Private)
    val app2 = app2Factory(app1, AppMode.Private)
    this.block(app1, app2)
  }
}

/**
 * Defines the mode in which a test wallet app should be launched.
 * - Legacy: The traditional wallet, without descriptor privacy.
 * - Private: The wallet with descriptor privacy.
 */
enum class AppMode { Legacy, Private }

/*
 * Launches an app in the specified mode.
 */
private suspend fun TestScope.launchAppForMode(mode: AppMode): AppTester =
  when (mode) {
    AppMode.Legacy -> launchNewApp()
    AppMode.Private -> launchPrivateWalletApp()
  }
