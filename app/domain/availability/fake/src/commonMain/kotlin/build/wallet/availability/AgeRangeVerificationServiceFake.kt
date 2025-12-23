package build.wallet.availability

/**
 * Fake implementation for testing.
 */
class AgeRangeVerificationServiceFake : AgeRangeVerificationService {
  var result: AgeRangeVerificationResult = AgeRangeVerificationResult.Allowed

  override suspend fun verifyAgeRange(): AgeRangeVerificationResult = result

  fun reset() {
    result = AgeRangeVerificationResult.Allowed
  }
}
