package build.wallet.availability

/**
 * Service for verifying user's age range for App Store Accountability Act compliance.
 *
 * Performs platform-specific age verification during onboarding to comply with
 * App Store Accountability Act requirements (Texas SB2420, etc.).
 */
interface AgeRangeVerificationService {
  /**
   * Verifies user's age range using platform APIs for App Store Accountability Act compliance.
   *
   * ## Platform Behavior
   *
   * ### iOS (26.2+)
   * Uses Apple's DeclaredAgeRange framework:
   * 1. First checks `isEligibleForAgeFeatures` to determine if user is in an applicable
   *    jurisdiction (e.g., Texas). If false, returns [AgeRangeVerificationResult.Allowed] immediately.
   * 2. If eligible, calls `requestAgeRange(ageGates:in:)` which shows a **system dialog**
   *    asking the user to share their age range. The dialog appearance depends on the user's
   *    "Age Range for Apps" setting in Apple Account → Personal Information:
   *    - "Share Automatically": No dialog shown, age range shared immediately
   *    - "Ask Before Sharing": Dialog shown each time
   *    - "Don't Share": No dialog shown, returns declined
   * 3. Response contains age range (e.g., lowerBound=18 means "18 or older")
   *
   * For iOS < 26.2, returns [AgeRangeVerificationResult.Allowed] (API not available).
   *
   * ### Android
   * Uses Google Play Age Signals API:
   * 1. Calls `AgeSignalsManager.checkAgeSignals()` to get user's age verification status
   * 2. Returns age range based on jurisdiction requirements (0-12, 13-15, 16-17, 18+)
   * 3. For supervised accounts (Family Link), parental approval may be required
   *
   * The API only returns data for users in applicable jurisdictions (currently Texas, Utah,
   * Louisiana). For other users, returns [AgeRangeVerificationResult.Allowed].
   *
   * ## Return Values
   * - [AgeRangeVerificationResult.Allowed]: User is 18+, declined sharing (fail-open),
   *   API unavailable, not in applicable jurisdiction, or error occurred (fail-open)
   * - [AgeRangeVerificationResult.Denied]: User is under 18 (lowerBound < 18 on iOS,
   *   supervised account status on Android)
   *
   * ## Bypasses
   * Returns [AgeRangeVerificationResult.Allowed] immediately without calling platform APIs when:
   * - App is Emergency variant (EEK) - emergency access should not be blocked
   * - Feature flag is disabled
   *
   * @return Age range verification result.
   */
  suspend fun verifyAgeRange(): AgeRangeVerificationResult
}
