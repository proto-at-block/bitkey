package build.wallet.analytics.events

import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_181_TO_365_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_31_TO_90_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_91_TO_180_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_OVER_365_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class SecurityPatchAgeBucketTests : FunSpec({
  val today = LocalDate(2026, 8, 26)

  test("bucket patch dates by age") {
    securityPatchAgeBucket("2026-08-26", today) shouldBe SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS
    securityPatchAgeBucket("2026-07-27", today) shouldBe SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS
    securityPatchAgeBucket("2026-07-26", today) shouldBe SECURITY_PATCH_AGE_BUCKET_31_TO_90_DAYS
    securityPatchAgeBucket("2026-05-28", today) shouldBe SECURITY_PATCH_AGE_BUCKET_31_TO_90_DAYS
    securityPatchAgeBucket("2026-05-27", today) shouldBe SECURITY_PATCH_AGE_BUCKET_91_TO_180_DAYS
    securityPatchAgeBucket("2026-02-27", today) shouldBe SECURITY_PATCH_AGE_BUCKET_91_TO_180_DAYS
    securityPatchAgeBucket("2026-02-26", today) shouldBe SECURITY_PATCH_AGE_BUCKET_181_TO_365_DAYS
    securityPatchAgeBucket("2025-08-26", today) shouldBe SECURITY_PATCH_AGE_BUCKET_181_TO_365_DAYS
    securityPatchAgeBucket("2025-08-25", today) shouldBe SECURITY_PATCH_AGE_BUCKET_OVER_365_DAYS
  }

  test("return unspecified for missing malformed or future patch dates") {
    securityPatchAgeBucket("", today) shouldBe SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
    securityPatchAgeBucket("not-a-date", today) shouldBe SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
    securityPatchAgeBucket("2026-08-27", today) shouldBe SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
  }
})
