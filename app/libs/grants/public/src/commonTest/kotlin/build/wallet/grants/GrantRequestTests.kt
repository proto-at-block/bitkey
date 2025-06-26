package build.wallet.grants

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

class GrantRequestTests : FunSpec({

  val testVersion: Byte = 1
  val testDeviceId = Random.nextBytes(8)
  val testChallenge = Random.nextBytes(16)
  val testSignature = Random.nextBytes(64)
  val testAction = GrantAction.FINGERPRINT_RESET

  context("GrantRequest equals and hashCode") {
    val baseRequest =
      GrantRequest(testVersion, testDeviceId, testChallenge, testAction, testSignature)

    test("reflexivity - an object should be equal to itself") {
      baseRequest shouldBe baseRequest
    }

    test("symmetry - if a equals b, then b should equal a") {
      val sameRequest = GrantRequest(testVersion, testDeviceId.copyOf(), testChallenge.copyOf(), testAction, testSignature.copyOf())
      (baseRequest == sameRequest) shouldBe true
      (sameRequest == baseRequest) shouldBe true
      baseRequest.hashCode() shouldBe sameRequest.hashCode()
    }

    test("objects with different version should not be equal") {
      val differentRequest = GrantRequest(2.toByte(), testDeviceId, testChallenge, testAction, testSignature)
      baseRequest shouldNotBe differentRequest
    }

    test("objects with different deviceId should not be equal") {
      val differentRequest = GrantRequest(testVersion, Random.nextBytes(8), testChallenge, testAction, testSignature)
      baseRequest shouldNotBe differentRequest
    }

    test("objects with different challenge should not be equal") {
      val differentRequest = GrantRequest(testVersion, testDeviceId, Random.nextBytes(16), testAction, testSignature)
      baseRequest shouldNotBe differentRequest
    }

    test("objects with different action should not be equal") {
      val differentRequest = GrantRequest(testVersion, testDeviceId, testChallenge, GrantAction.TRANSACTION_VERIFICATION, testSignature)
      baseRequest shouldNotBe differentRequest
    }

    test("objects with different signature should not be equal") {
      val differentRequest = GrantRequest(testVersion, testDeviceId, testChallenge, testAction, Random.nextBytes(64))
      baseRequest shouldNotBe differentRequest
    }

    test("equality with null should be false") {
      (baseRequest.equals(null)) shouldBe false
    }

    test("equality with different type should be false") {
      (baseRequest.equals("a string")) shouldBe false
    }
  }
})
