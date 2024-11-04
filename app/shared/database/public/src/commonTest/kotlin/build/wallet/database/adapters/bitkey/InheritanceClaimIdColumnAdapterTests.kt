package build.wallet.database.adapters.bitkey

import build.wallet.bitkey.inheritance.InheritanceClaimId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InheritanceClaimIdColumnAdapterTests : FunSpec({

  val claimId = InheritanceClaimId("claim-id")

  test("decode ClaimId from string") {
    InheritanceClaimIdColumnAdapter.decode(claimId.value).shouldBe(claimId)
  }

  test("encode ClaimId to string") {
    InheritanceClaimIdColumnAdapter.encode(claimId).shouldBe(claimId.value)
  }
})
