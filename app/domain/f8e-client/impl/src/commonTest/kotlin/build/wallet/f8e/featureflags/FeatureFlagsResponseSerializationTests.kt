package build.wallet.f8e.featureflags

import build.wallet.f8e.featureflags.FeatureFlagsF8eClientImpl.FeatureFlagsResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class FeatureFlagsResponseSerializationTests : FunSpec({

  test("Decode ignores invalid feature flag data") {
    val input = """
    {
     "flags": [
      {
        "key":"key1",
        "value":{"boolean": true}
      },
      {
        "key":"key2",
        "name":"Name",
        "value":{"unknowntype":1.01}
       },
       {
        "key":"key3",
        "value":{"boolean":false}
       }
     ]
    }
    """.trimIndent()

    val featureFlagResponse = Json.decodeFromString<FeatureFlagsResponse>(input)

    val result = featureFlagResponse.decodeValidFlags()
    result.count().shouldBe(2)
    result[0].key.shouldBe("key1")
    result[1].key.shouldBe("key3")
  }
})
