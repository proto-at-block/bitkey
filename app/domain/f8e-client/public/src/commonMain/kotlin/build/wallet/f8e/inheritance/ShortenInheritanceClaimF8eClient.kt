package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.InheritanceClaimId
import com.github.michaelbull.result.Result
import kotlin.time.Duration

/**
 * Shortens the inheritance claim process for a beneficiary.
 *
 * Note: This only works for test accounts! For obvious reasons, this endpoint
 * is restricted to test accounts, and is used to shorten the delay period
 * for automated tests only.
 */
interface ShortenInheritanceClaimF8eClient {
  suspend fun shortenClaim(
    fullAccount: FullAccount,
    claimId: InheritanceClaimId,
    delay: Duration,
  ): Result<Unit, Throwable>
}
