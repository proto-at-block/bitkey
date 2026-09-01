package build.wallet.availability

/**
 * Fake implementation for testing.
 */
class AgeRangeVerificationServiceFake : AgeRangeVerificationService {
  var result: AgeRangeVerificationResult = AgeRangeVerificationResult.Allowed

  /** Number of times [verifyAgeRange] has been invoked. */
  var verifyAgeRangeCalls: Int = 0
    private set

  override suspend fun verifyAgeRange(): AgeRangeVerificationResult {
    verifyAgeRangeCalls++
    return result
  }

  fun reset() {
    result = AgeRangeVerificationResult.Allowed
    verifyAgeRangeCalls = 0
  }
}
