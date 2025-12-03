package build.wallet.firmware.grant

import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.grants.serializeToPackedStruct
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class GrantSerializationTests : FunSpec({

  val testVersion: Byte = 1
  val testDeviceId = Random.nextBytes(8)
  val testChallenge = Random.nextBytes(16)
  val testSignature = Random.nextBytes(64)

  context("GrantRequest serialization") {
    test("should correctly serialize FINGERPRINT_RESET action") {
      val grantRequest = GrantRequest(
        version = testVersion,
        deviceId = testDeviceId,
        challenge = testChallenge,
        action = GrantAction.FINGERPRINT_RESET,
        signature = testSignature
      )

      val serialized = grantRequest.serializeToPackedStruct()!!

      // Expected length: 1 (version) + 8 (deviceId) + 16 (challenge) + 1 (action) + 64 (signature) = 90
      serialized.size shouldBe 90

      var offset = 0
      serialized[offset] shouldBe testVersion
      offset += 1

      serialized.sliceArray(offset until offset + testDeviceId.size) shouldBe testDeviceId
      offset += testDeviceId.size

      serialized.sliceArray(offset until offset + testChallenge.size) shouldBe testChallenge
      offset += testChallenge.size

      val expectedActionByte: Byte = 1
      serialized[offset] shouldBe expectedActionByte
      offset += 1

      serialized.sliceArray(offset until offset + testSignature.size) shouldBe testSignature
    }

    test("should correctly serialize TRANSACTION_VERIFICATION action") {
      val grantRequest = GrantRequest(
        version = testVersion,
        deviceId = testDeviceId,
        challenge = testChallenge,
        action = GrantAction.TRANSACTION_VERIFICATION,
        signature = testSignature
      )

      val serialized = grantRequest.serializeToPackedStruct()!!

      serialized.size shouldBe 90

      val expectedActionByte: Byte = 2
      serialized[1 + testDeviceId.size + testChallenge.size] shouldBe expectedActionByte
    }
  }

  context("Grant serialization") {
    test("should correctly serialize a Grant") {
      val grantRequest = GrantRequest(
        version = testVersion,
        deviceId = testDeviceId,
        challenge = testChallenge,
        action = GrantAction.FINGERPRINT_RESET,
        signature = testSignature
      )
      val serializedGrantRequest = grantRequest.serializeToPackedStruct()!!
      val grantSignature = Random.nextBytes(64)

      val grant = Grant(
        version = testVersion,
        serializedRequest = serializedGrantRequest,
        appSignature = grantSignature,
        wsmSignature = grantSignature
      )

      val serializedGrant = grant.serializeToPackedStruct()!!

      // Expected length: 1 (version) + 90 (serializedRequest) + 64 (appSignature) + 64 (wsmSignature) = 219
      serializedGrant.size shouldBe (1 + serializedGrantRequest.size + grantSignature.size + grantSignature.size)

      var offset = 0
      serializedGrant[offset] shouldBe testVersion
      offset += 1

      serializedGrant.sliceArray(offset until offset + serializedGrantRequest.size) shouldBe serializedGrantRequest
      offset += serializedGrantRequest.size

      serializedGrant.sliceArray(offset until offset + grantSignature.size) shouldBe grantSignature
      offset += grantSignature.size

      serializedGrant.sliceArray(offset until offset + grantSignature.size) shouldBe grantSignature
    }
  }
})
