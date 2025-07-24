package build.wallet.fwup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class SemverUtilsTests :
  FunSpec({

    test("Semver to int conversion") {
      semverToInt("1.2.5") shouldBe 102005
      semverToInt("1.0.65") shouldBe 100065
      semverToInt("1.0.0") shouldBe 100000
      semverToInt("9.09.999") shouldBe 909999

      semverToInt("1.1.2") shouldBeLessThan semverToInt("1.1.3")
      semverToInt("1.0.999") shouldBeLessThan semverToInt("1.1.0")
      semverToInt("1.9.9") shouldBeLessThan semverToInt("1.10.0")

      semverToInt("0.0.1") shouldBe "0000001".toInt()
      semverToInt("0.0.0") shouldBe "0000000".toInt()
      semverToInt("0.99.0") shouldBe "0099000".toInt()
      semverToInt("99.0.0") shouldBe "9900000".toInt()
      semverToInt("0.1.0") shouldBe "0001000".toInt()

      semverToInt("1.10.1") shouldBeLessThan semverToInt("1.11.0")
      semverToInt("1.2.10") shouldBeLessThan semverToInt("1.2.11")
      semverToInt("10.0.0") shouldBeGreaterThan semverToInt("9.99.999")
    }

    test("Major version bounds checking") {
      val exception = shouldThrow<IllegalArgumentException> {
        semverToInt("100.0.0")
      }
      exception.message shouldContain "Major version must be less than 100, got: 100"

      val exception2 = shouldThrow<IllegalArgumentException> {
        semverToInt("999.0.0")
      }
      exception2.message shouldContain "Major version must be less than 100, got: 999"
    }

    test("Minor version bounds checking") {
      val exception = shouldThrow<IllegalArgumentException> {
        semverToInt("1.100.0")
      }
      exception.message shouldContain "Minor version must be less than 100, got: 100"

      val exception2 = shouldThrow<IllegalArgumentException> {
        semverToInt("0.500.0")
      }
      exception2.message shouldContain "Minor version must be less than 100, got: 500"
    }

    test("Patch version bounds checking") {
      val exception = shouldThrow<IllegalArgumentException> {
        semverToInt("1.1.1000")
      }
      exception.message shouldContain "Patch version must be less than 1000, got: 1000"

      val exception2 = shouldThrow<IllegalArgumentException> {
        semverToInt("0.0.9999")
      }
      exception2.message shouldContain "Patch version must be less than 1000, got: 9999"
    }

    test("Edge cases within bounds") {
      // Test maximum valid values
      semverToInt("99.99.999") shouldBe 9999999
      semverToInt("0.0.999") shouldBe 999
      semverToInt("99.0.0") shouldBe 9900000
      semverToInt("0.99.0") shouldBe 99000
    }

    test("Multiple bounds violations") {
      val exception = shouldThrow<IllegalArgumentException> {
        semverToInt("100.100.1000")
      }
      // Should fail on the first check (major)
      exception.message shouldContain "Major version must be less than 100, got: 100"
    }
  })
