package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.encrypt.XCiphertext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class BeneficiaryClaimSerializerTests : FunSpec({
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
                "delay_start_time": "2021-03-15T19:18:17Z",
                "auth_keys": {
                    "app_pubkey": "test-app_pubkey",
                    "hardware_pubkey": "test-hardware_pubkey"
                }
            }
  """.trimIndent()
  val canceledClaim = """
            {
                "status": "CANCELED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id"
            }
  """.trimIndent()
  val lockedClaimNoSealedDescriptor = """
            {
                "status": "LOCKED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id",
                "sealed_dek": "test-sealed_dek",
                "sealed_mobile_key": "test-sealed_mobile_key",
                "benefactor_descriptor_keyset": "test-keyset"
            }
  """.trimIndent()
  val lockedClaimBothDescriptors = """
            {
                "status": "LOCKED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id",
                "sealed_dek": "test-sealed_dek",
                "sealed_mobile_key": "test-sealed_mobile_key",
                "sealed_descriptor": "test-sealed_descriptor",
                "benefactor_descriptor_keyset": "test-keyset"
            }
  """.trimIndent()
  val lockedClaimNoPlaintextDescriptor = """
            {
                "status": "LOCKED",
                "id": "test-id",
                "recovery_relationship_id": "test-relationship_id",
                "sealed_dek": "test-sealed_dek",
                "sealed_mobile_key": "test-sealed_mobile_key",
                "sealed_descriptor": "test-sealed_descriptor"
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
    val result = json.decodeFromString(BeneficiaryClaimSerializer, pendingClaimJson)

    result.shouldBeInstanceOf<BeneficiaryClaim.PendingClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.delayStartTime.toEpochMilliseconds().shouldBe(1615835897000)
    result.delayEndTime.toEpochMilliseconds().shouldBe(1631733497000)
    result.authKeys.appPubkey.shouldBe("test-app_pubkey")
    result.authKeys.hardwarePubkey.shouldBe("test-hardware_pubkey")
  }

  test("Serialize beneficiary pending claim") {
    val input = BeneficiaryClaim.PendingClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id"),
      delayEndTime = Instant.fromEpochMilliseconds(1631733497000),
      delayStartTime = Instant.fromEpochMilliseconds(1615835897000),
      authKeys = InheritanceClaimKeyset(
        appPubkey = "test-app_pubkey",
        hardwarePubkey = "test-hardware_pubkey"
      )
    )

    val result = json.encodeToString(BeneficiaryClaimSerializer, input)

    result.shouldBe(pendingClaimJson)
  }

  test("Deserialize canceled claim") {
    val result = json.decodeFromString(BeneficiaryClaimSerializer, canceledClaim)

    result.shouldBeInstanceOf<BeneficiaryClaim.CanceledClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
  }

  test("Serialize canceled claim") {
    val input = BeneficiaryClaim.CanceledClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id")
    )

    val result = json.encodeToString(BeneficiaryClaimSerializer, input)

    result.shouldBe(canceledClaim)
  }

  test("Deserialize locked claim w/ no sealed descriptor") {
    val result = json.decodeFromString(BeneficiaryClaimSerializer, lockedClaimNoSealedDescriptor)

    result.shouldBeInstanceOf<BeneficiaryClaim.LockedClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.sealedDek.value.shouldBe("test-sealed_dek")
    result.sealedMobileKey.value.shouldBe("test-sealed_mobile_key")
    result.sealedDescriptor?.value.shouldBe(null)
    result.benefactorKeyset?.value.shouldBe("test-keyset")
  }

  test("Deserialize locked claim w/ both descriptors") {
    val result = json.decodeFromString(BeneficiaryClaimSerializer, lockedClaimBothDescriptors)

    result.shouldBeInstanceOf<BeneficiaryClaim.LockedClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.sealedDek.value.shouldBe("test-sealed_dek")
    result.sealedMobileKey.value.shouldBe("test-sealed_mobile_key")
    result.sealedDescriptor?.value.shouldBe("test-sealed_descriptor")
    result.benefactorKeyset?.value.shouldBe("test-keyset")
  }

  test("Deserialize locked claim w/ no plaintext descriptor") {
    val result = json.decodeFromString(BeneficiaryClaimSerializer, lockedClaimNoPlaintextDescriptor)

    result.shouldBeInstanceOf<BeneficiaryClaim.LockedClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.sealedDek.value.shouldBe("test-sealed_dek")
    result.sealedMobileKey.value.shouldBe("test-sealed_mobile_key")
    result.sealedDescriptor?.value.shouldBe("test-sealed_descriptor")
    result.benefactorKeyset?.value.shouldBe(null)
  }

  test("Serialize locked claim w/ no sealed descriptor") {
    val input = BeneficiaryClaim.LockedClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id"),
      sealedDek = XCiphertext("test-sealed_dek"),
      sealedMobileKey = XCiphertext("test-sealed_mobile_key"),
      sealedDescriptor = null,
      benefactorKeyset = BenefactorDescriptorKeyset("test-keyset")
    )

    val result = json.encodeToString(BeneficiaryClaimSerializer, input)

    result.shouldBe(lockedClaimNoSealedDescriptor)
  }

  test("Serialize locked claim w/ both descriptors") {
    val input = BeneficiaryClaim.LockedClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id"),
      sealedDek = XCiphertext("test-sealed_dek"),
      sealedMobileKey = XCiphertext("test-sealed_mobile_key"),
      sealedDescriptor = XCiphertext("test-sealed_descriptor"),
      benefactorKeyset = BenefactorDescriptorKeyset("test-keyset")
    )

    val result = json.encodeToString(BeneficiaryClaimSerializer, input)

    result.shouldBe(lockedClaimBothDescriptors)
  }

  test("Serialize locked claim w/ no plaintext descriptor") {
    val input = BeneficiaryClaim.LockedClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id"),
      sealedDek = XCiphertext("test-sealed_dek"),
      sealedMobileKey = XCiphertext("test-sealed_mobile_key"),
      sealedDescriptor = XCiphertext("test-sealed_descriptor"),
      benefactorKeyset = null
    )

    val result = json.encodeToString(BeneficiaryClaimSerializer, input)

    result.shouldBe(lockedClaimNoPlaintextDescriptor)
  }

  test("Deserialize complete claim") {
    val result = json.decodeFromString(BeneficiaryClaimSerializer, completeClaim)

    result.shouldBeInstanceOf<BeneficiaryClaim.CompleteClaim>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
  }

  test("Serialize Complete Claim") {
    val input = BeneficiaryClaim.CompleteClaim(
      claimId = InheritanceClaimId("test-id"),
      relationshipId = RelationshipId("test-relationship_id")
    )

    val result = json.encodeToString(BeneficiaryClaimSerializer, input)

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

    val result = json.decodeFromString(BeneficiaryClaimSerializer, unknownClaim)

    result.shouldBeInstanceOf<BeneficiaryClaim.UnknownStatus>()
    result.claimId.value.shouldBe("test-id")
    result.relationshipId.value.shouldBe("test-relationship_id")
    result.status.key.shouldBe("FancyFutureState")
  }
})
