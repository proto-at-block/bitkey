package build.wallet.firmware.grant

import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.grants.serializeToPackedStruct
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

class GrantTests : FunSpec({
  val testVersion: Byte = 1
  val testDeviceId = Random.nextBytes(8)
  val testChallenge = Random.nextBytes(16)
  val testSignature = Random.nextBytes(64)
  val testAction = GrantAction.FINGERPRINT_RESET

  context("Grant equals and hashCode") {
    val baseGrantRequestBytes = GrantRequest(
      testVersion,
      testDeviceId,
      testChallenge,
      testAction,
      testSignature
    ).serializeToPackedStruct()!!
    val baseGrantSignature = Random.nextBytes(64)
    val baseGrant = Grant(testVersion, baseGrantRequestBytes, baseGrantSignature)

    test("reflexivity - an object should be equal to itself") {
      baseGrant shouldBe baseGrant
    }

    test("symmetry - if a equals b, then b should equal a") {
      val sameGrant =
        Grant(testVersion, baseGrantRequestBytes.copyOf(), baseGrantSignature.copyOf())
      (baseGrant == sameGrant) shouldBe true
      (sameGrant == baseGrant) shouldBe true
      baseGrant.hashCode() shouldBe sameGrant.hashCode()
    }

    test("objects with different version should not be equal") {
      val differentGrant = Grant(2.toByte(), baseGrantRequestBytes, baseGrantSignature)
      baseGrant shouldNotBe differentGrant
    }

    test("objects with different serializedRequest should not be equal") {
      val differentRequestBytes = GrantRequest(
        testVersion,
        Random.nextBytes(8),
        testChallenge,
        testAction,
        testSignature
      ).serializeToPackedStruct()!!
      val differentGrant = Grant(testVersion, differentRequestBytes, baseGrantSignature)
      baseGrant shouldNotBe differentGrant
    }

    test("objects with different signature should not be equal") {
      val differentGrant = Grant(testVersion, baseGrantRequestBytes, Random.nextBytes(64))
      baseGrant shouldNotBe differentGrant
    }

    test("equality with null should be false") {
      (baseGrant.equals(null)) shouldBe false
    }

    test("equality with different type should be false") {
      (baseGrant.equals("a string")) shouldBe false
    }
  }
})
