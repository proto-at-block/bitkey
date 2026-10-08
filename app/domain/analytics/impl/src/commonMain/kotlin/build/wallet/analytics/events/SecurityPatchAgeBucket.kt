package build.wallet.analytics.events

import build.wallet.analytics.v1.SecurityPatchAgeBucket
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_181_TO_365_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_31_TO_90_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_91_TO_180_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_OVER_365_DAYS
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
import build.wallet.catchingResult
import com.github.michaelbull.result.get
import kotlinx.datetime.LocalDate

internal fun securityPatchAgeBucket(
  securityPatch: String,
  today: LocalDate,
): SecurityPatchAgeBucket {
  val patchDate = catchingResult { LocalDate.parse(securityPatch) }.get()
    ?: return SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
  val ageInDays = today.toEpochDays() - patchDate.toEpochDays()

  return when {
    ageInDays < 0 -> SECURITY_PATCH_AGE_BUCKET_UNSPECIFIED
    ageInDays <= 30 -> SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS
    ageInDays <= 90 -> SECURITY_PATCH_AGE_BUCKET_31_TO_90_DAYS
    ageInDays <= 180 -> SECURITY_PATCH_AGE_BUCKET_91_TO_180_DAYS
    ageInDays <= 365 -> SECURITY_PATCH_AGE_BUCKET_181_TO_365_DAYS
    else -> SECURITY_PATCH_AGE_BUCKET_OVER_365_DAYS
  }
}
