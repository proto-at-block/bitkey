package build.wallet.testing.ext

import build.wallet.testing.AppTester

/**
 * Returns the last text sent to platform Share Sheet.
 */
val AppTester.lastSharedText get() = sharingManager.lastSharedText.value
