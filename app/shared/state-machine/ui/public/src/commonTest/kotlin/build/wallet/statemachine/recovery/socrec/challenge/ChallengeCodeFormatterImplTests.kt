package build.wallet.statemachine.recovery.socrec.challenge

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChallengeCodeFormatterImplTests : FunSpec({

  val formatter = ChallengeCodeFormatterImpl()

  test("4-4-2") {
    formatter.format("1234567891").shouldBe("1234-5678-91")
  }

  test("4-4-x") {
    formatter.format("123456789111").shouldBe("1234-5678-9111")
  }

  test("short") {
    formatter.format("123456").shouldBe("1234-56")
  }

  test("alphanumeric") {
    formatter.format("1A23X45Z60BMNCS").shouldBe("1A23-X45Z-60BM-NCS")
  }
})
