package build.wallet.testing.tags

import io.kotest.core.Tag

/**
 * Our custom Kotest test tags which allow us to specify which tests should be executed at runtime.
 *
 * For example, to run integration tests with ServerSmoke tag:
 * ```
 * gradle jvmIntegrationTest -Dkotest.tags='ServerSmoke'
 * ```
 *
 * See more: https://kotest.io/docs/next/framework/tags.html
 */
sealed class TestTag : Tag() {
  /**
   * Corresponds to integration smoke tests which should be ran as part of Server tests on CI.
   * See `wallet/.github/workflows/server.yml`.
   */
  object ServerSmoke : TestTag()
}
