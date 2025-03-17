package build.wallet

import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.negativeFloat
import io.kotest.property.checkAll

class ProgressTests : FunSpec({

  context("Double.asProgress()") {
    test("valid progress values between 0.0 and 1.0") {
      checkAll(Arb.float(0.0f, 1.0f)) { value ->
        value.asProgress().shouldBeOk().value.shouldBe(value)
      }
    }

    test("invalid progress values lower than 0.0") {
      checkAll(Arb.negativeFloat()) {
        it.asProgress().shouldBeErr(Error("Progress cannot be negative."))
      }
    }

    test("invalid progress values greater than 1.0") {
      checkAll(Arb.float(min = 1.0000001f)) {
        it.asProgress().shouldBeErr(Error("Progress cannot be greater than 1."))
      }
    }
  }

  test("Progress.Zero") {
    Progress.Zero.value.shouldBe(0.0)
  }

  test("Progress.Full") {
    Progress.Full.value.shouldBe(1.0)
  }

  context("Progress#toString()") {
    test("0%") {
      0.0f.asProgress().shouldBeOk().toString().shouldBe("0%")
    }

    test("50%") {
      0.5f.asProgress().shouldBeOk().toString().shouldBe("50%")
    }

    test("100%") {
      1.0f.asProgress().shouldBeOk().toString().shouldBe("100%")
    }

    test("1.41%") {
      0.0141f.asProgress().shouldBeOk().toString().shouldBe("1.41%")
    }

    test("1.5%") {
      0.015f.asProgress().shouldBeOk().toString().shouldBe("1.5%")
    }

    test("99.999%") {
      0.99999f.asProgress().shouldBeOk().toString().shouldBe("99.999%")
    }
  }
})
