package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DelegatedColumnAdapterTest : FunSpec({
  test("Test Chaining") {
    val adapter1 = object : ColumnAdapter<String, String> {
      override fun decode(databaseValue: String) = "${databaseValue}A"

      override fun encode(value: String) = "${value}B"
    }
    val adapter2 = object : ColumnAdapter<String, String> {
      override fun decode(databaseValue: String) = "${databaseValue}C"

      override fun encode(value: String) = "${value}D"
    }

    // Encoding starts with the first adapter and proceeds down the chain:
    adapter1.then(adapter2).encode("").shouldBe("BD")

    // Decode starts with the last adapter and works backwards:
    adapter1.then(adapter2).decode("").shouldBe("CA")
  }
})
