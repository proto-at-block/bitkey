package build.wallet.debug.cloud

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CloudBackupStoreTypeTests : FunSpec({
  test("store types are available, unique, and names are non-empty") {
    val types = availableCloudBackupStoreTypes()

    types.isNotEmpty() shouldBe true
    types.toSet().size shouldBe types.size

    types.forEach { type ->
      type.name.isNotBlank() shouldBe true
    }
  }
})
