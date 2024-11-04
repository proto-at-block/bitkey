package build.wallet.database.adapters.bitkey

import build.wallet.bitkey.relationships.RelationshipId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RelationshipIdColumnAdapterTests : FunSpec({

  val relationshipId = RelationshipId("relationship-id")

  test("decode RelationshipId from string") {
    RelationshipIdColumnAdapter.decode(relationshipId.value).shouldBe(relationshipId)
  }

  test("encode RelationshipId to string") {
    RelationshipIdColumnAdapter.encode(relationshipId).shouldBe(relationshipId.value)
  }
})
