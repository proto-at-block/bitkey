package build.wallet.platform.random

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContainDuplicates
import io.kotest.matchers.string.shouldMatch

class UuidTests : FunSpec({

  val uuid = UuidGeneratorImpl()
  val uuidRegex =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

  test("generate uuid") {
    // generate a bunch of UUIDs from UuidImpl interface and uuid() function.
    val uuids = List(100) { uuid() } + List(100) { uuid.random() }

    // Should be all unique
    uuids.shouldNotContainDuplicates()

    // Should match UUID format
    uuids.forEach {
      it shouldMatch uuidRegex
    }
  }
})
