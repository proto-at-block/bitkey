package build.wallet.crypto

import build.wallet.rust.core.WsmIntegrityVerifierException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WsmVerifierTests : FunSpec(
  {

    val verifier = WsmVerifierImpl()
    val message =
      "tpubDDXfJeMxwsGKevLkFsJ9LDc4m999EYHmzgh161aD94FeJFKPuLFfGB57CA2EJgM18DFYL9vq6oXodXpDTdbZg6k6UqnoHhYmh4n6CYe1KmD"
    val validSignature =
      "aa0d883dff66d7d627369e46458faf1bbb7c41e4365ab52541e0d4333b79839218e3dcecb34ee1b671fcf382bf10277ace84b5943e2599eef92d576b5a7a156d"
    val invalidSignature =
      "ab0d883dff66d7d627369e46458faf1bbb7c41e4365ab52541e0d4333b79839218e3dcecb34ee1b671fcf382bf10277ace84b5943e2599eef92d576b5a7a156d"
    val malformedSignature =
      "jlaksdfjklasdfj"

    val keyVariant = WsmIntegrityKeyVariant.Test

    context("WsmVerifier verify method") {
      test("should verify a valid message and signature") {
        verifier.verify(message, validSignature, keyVariant).isValid shouldBe true
      }

      test("should not verify an invalid message and signature") {
        verifier.verify(
          message,
          invalidSignature,
          keyVariant
        ).isValid shouldBe false
      }

      test("should not verify with malformed signature") {
        shouldThrow<WsmIntegrityVerifierException.Base16DecodeFailure> {
          verifier.verify(
            message,
            malformedSignature,
            keyVariant
          ).isValid shouldBe false
        }
      }
    }
  }
)
