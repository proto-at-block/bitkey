package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class MissingFeatureFlagInListTest {
  @Test
  fun `reports missing feature flag in list`() {
    val code =
      """
      import build.wallet.feature.FeatureFlag
      import build.wallet.feature.FeatureFlagValue
      import me.tatarka.inject.annotations.Provides

      interface FeatureFlagsComponent {
        @Provides fun fooFeatureFlag() = FooFeatureFlag()

        @Provides
        fun featureFlags(
          fooFeatureFlag: FooFeatureFlag,
        ): List<FeatureFlag<out FeatureFlagValue>> {
          return listOf()
        }
      }

      class FooFeatureFlag
      """.trimIndent()

    val findings = MissingFeatureFlagInList(Config.empty).lint(code)

    assertEquals(1, findings.size)
    assertEquals("MissingFeatureFlagInList", findings.first().id)
    assertEquals("featureFlags() is missing: FooFeatureFlag", findings.first().message)
  }

  @Test
  fun `does not report when feature flag is included`() {
    val code =
      """
      import build.wallet.feature.FeatureFlag
      import build.wallet.feature.FeatureFlagValue
      import me.tatarka.inject.annotations.Provides

      interface FeatureFlagsComponent {
        @Provides fun fooFeatureFlag() = FooFeatureFlag()

        @Provides
        fun featureFlags(
          fooFeatureFlag: FooFeatureFlag,
        ): List<FeatureFlag<out FeatureFlagValue>> {
          return listOf(fooFeatureFlag)
        }
      }

      class FooFeatureFlag
      """.trimIndent()

    val findings = MissingFeatureFlagInList(Config.empty).lint(code)

    assertEquals(0, findings.size)
  }
}
