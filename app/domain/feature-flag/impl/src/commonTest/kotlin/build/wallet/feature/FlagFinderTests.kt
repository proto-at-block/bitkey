package build.wallet.feature

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class FlagFinderTests : FunSpec({
  val flagFinderFactory = FlagFinderFactoryImpl()
  val dao = FeatureFlagDaoFake()
  val featureFlags = listOf(
    FirstMobileTestFeatureFlag(featureFlagDao = dao),
    SecondFlagMobileTestFeatureFlag(featureFlagDao = dao),
    ThirdFlagMobileTestFeatureFlag(featureFlagDao = dao)
  )
  val featureFlagsFinder = flagFinderFactory.index(featureFlags)

  featureFlags.forEach { featureFlag ->
    context("search for ${featureFlag.title}") {
      val featureFlagClass = featureFlag::class
      var query = ""
      var jaggedQuery = ""
      beforeTest {
        query = ""
        jaggedQuery = ""
      }

      featureFlag.title.map { c ->
        query += c
        query
      }
        .distinct()
        .forEach { query ->
          context("query: $query") {
            test("just find it") {
              val result = featureFlagsFinder.find(query)
              result.find { featureFlagClass.isInstance(it) }.shouldNotBeNull()
            }

            test("must be first result: $query") {
              val result = featureFlagsFinder.find(query)
              if (query.length > 3) {
                val first = result.first()
                featureFlagClass.isInstance(first)
              } else {
                result.find { featureFlagClass.isInstance(it) }.shouldNotBeNull()
              }
            }

            test("uppercase query: $query") {
              val result = featureFlagsFinder.find(query.uppercase())
              result.find { featureFlagClass.isInstance(it) }.shouldNotBeNull()
            }
          }
        }

      featureFlag.title.map { c ->
        if (c.uppercaseChar() == c && c != ' ') {
          jaggedQuery += c
        }
        jaggedQuery
      }
        .distinct()
        .forEach { jaggedQuery ->
          context("jaggedQuery: $jaggedQuery") {
            test("just find it") {
              val result = featureFlagsFinder.find(jaggedQuery)
              result.find { featureFlagClass.isInstance(it) }.shouldNotBeNull()
            }

            test("must be first result: $jaggedQuery") {
              val result = featureFlagsFinder.find(jaggedQuery)
              if (jaggedQuery.length > 3) {
                val first = result.first()
                featureFlagClass.isInstance(first)
              } else {
                result.find { featureFlagClass.isInstance(it) }.shouldNotBeNull()
              }
            }
          }
        }
    }
  }
})

class FirstMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-test-flag-double",
    title = "First Mobile Test Feature Flag",
    description = "This is a test flag with a Number type",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(0.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )

class SecondFlagMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "mobile-test-flag-string",
    title = "Second Mobile Test Feature Flag",
    description = "This is a test flag with a String type",
    defaultFlagValue = FeatureFlagValue.StringFlag(""),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )

class ThirdFlagMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "mobile-test-flag-string",
    title = "Third Mobile Test Feature Flag",
    description = "This is a test flag with a String type",
    defaultFlagValue = FeatureFlagValue.StringFlag(""),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )
