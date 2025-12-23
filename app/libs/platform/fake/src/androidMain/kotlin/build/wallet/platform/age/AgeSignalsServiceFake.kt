package build.wallet.platform.age

/**
 * Fake implementation of [AgeSignalsService] for testing.
 */
class AgeSignalsServiceFake : AgeSignalsService {
  var response: AgeSignalsResponse = AgeSignalsResponse.Verified

  override suspend fun checkAgeSignals(): AgeSignalsResponse = response

  fun reset() {
    response = AgeSignalsResponse.Verified
  }
}
