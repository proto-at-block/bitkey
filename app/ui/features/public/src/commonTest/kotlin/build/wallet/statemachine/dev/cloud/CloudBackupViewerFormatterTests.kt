package build.wallet.statemachine.dev.cloud

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain

class CloudBackupViewerFormatterTests : FunSpec({
  test("prettyValue pretty prints valid json") {
    val raw = """{"a":1,"b":{"c":2}}"""

    CloudBackupViewerFormatter.prettyValue(raw).shouldBe(
      """
      {
        "a": 1,
        "b": {
          "c": 2
        }
      }
      """.trimIndent()
    )
  }

  test("prettyValue returns raw when value is not json") {
    val raw = "{not-json"

    CloudBackupViewerFormatter.prettyValue(raw).shouldBe(raw)
  }

  test("previewValue returns compact single-line truncated text") {
    val raw = """{"value":"${"x".repeat(200)}"}"""

    val preview = CloudBackupViewerFormatter.previewValue(raw)

    preview.length.shouldBe(140)
    preview.shouldEndWith("…")
    preview.shouldNotContain("\n")
    preview.shouldContain("\"value\"")
  }

  test("cloudBackupStoreTitle formats Ubiquitous KVS store title") {
    cloudBackupStoreTitle("Ubiquitous KVS").shouldBe("UbiquitousKeyValueStore")
    cloudBackupStoreTitle("CloudKit").shouldBe("CloudKit")
  }

  test("cloudBackupStoreTitle labels fake stores") {
    cloudBackupStoreTitle("Google Drive", isFake = true).shouldBe("Cloud Storage (Fake)")
    cloudBackupStoreTitle("CloudKit", isFake = true).shouldBe("Cloud Storage (Fake)")
    cloudBackupStoreTitle("Ubiquitous KVS", isFake = true)
      .shouldBe("Cloud Storage (Fake)")
  }
})
