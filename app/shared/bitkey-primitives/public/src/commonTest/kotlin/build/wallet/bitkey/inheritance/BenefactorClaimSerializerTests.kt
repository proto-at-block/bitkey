package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class BenefactorClaimSerializerTests : FunSpec({
  val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }
  val pendingClaimJson = """
            {
                "status": "PENDING",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id",
                "delay_end_time": "2021-09-15T19:18:17Z",
                "delay_start_time": "2021-03-15T19:18:17Z"
            }
  """.trimIndent()
  val canceledClaim = """
            {
                "status": "CANCELED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id"
            }
  """.trimIndent()
  val lockedClaim = """
            {
                "status": "LOCKED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id"
            }
  """.trimIndent()
  val completeClaim = """
            {
                "status": "COMPLETED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id"
            }
  """.trimIndent()

  test("Deserialize pending claim") {
    val result = json.decodeFromString(BenefactorClaimSerializer, pendingClaimJson)

    result.shouldBeInstanceOf<BenefactorClaim.PendingClaim>()
    result.shouldBeInstanceOf<BenefactorClaim.PendingClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.delayStartTime.toEpochMilliseconds().shouldBe(1615835897000)
    result.delayEndTime.toEpochMilliseconds().shouldBe(1631733497000)
  }

  test("Serialize Benefactor pending claim") {
    val input = BenefactorClaim.PendingClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id"),
      delayEndTime = Instant.fromEpochMilliseconds(1631733497000),
      delayStartTime = Instant.fromEpochMilliseconds(1615835897000)
    )

    val result = json.encodeToString(BenefactorClaimSerializer, input)

    result.shouldBe(pendingClaimJson)
  }

  test("Deserialize canceled claim") {
    val result = json.decodeFromString(BenefactorClaimSerializer, canceledClaim)

    result.shouldBeInstanceOf<BenefactorClaim.CanceledClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
  }

  test("Serialize canceled claim") {
    val input = BenefactorClaim.CanceledClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id")
    )

    val result = json.encodeToString(BenefactorClaimSerializer, input)

    result.shouldBe(canceledClaim)
  }

  test("Deserialize locked claim") {
    val result = json.decodeFromString(BenefactorClaimSerializer, lockedClaim)

    result.shouldBeInstanceOf<BenefactorClaim.LockedClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
  }

  test("Serialize Locked Claim") {
    val input = BenefactorClaim.LockedClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id")
    )

    val result = json.encodeToString(BenefactorClaimSerializer, input)

    result.shouldBe(lockedClaim)
  }

  test("Deserialize complete claim") {
    val result = json.decodeFromString(BenefactorClaimSerializer, completeClaim)

    result.shouldBeInstanceOf<BenefactorClaim.CompleteClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
  }

  test("Serialize complete Claim") {
    val input = BenefactorClaim.CompleteClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id")
    )

    val result = json.encodeToString(BenefactorClaimSerializer, input)

    result.shouldBe(completeClaim)
  }

  test("Deserialize unknown state") {
    val unknownClaim = """
            {
                "status": "FancyFutureState",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id",
                "unknown_field": "test-unknown_field"
            }
    """.trimIndent()

    val result = json.decodeFromString(BenefactorClaimSerializer, unknownClaim)

    result.shouldBeInstanceOf<BenefactorClaim.UnknownStatus>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.status.key.shouldBe("FancyFutureState")
  }
})
