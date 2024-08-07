package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import dev.zacsweers.redacted.annotations.Redacted

/***
 * Social Recovery Challenge Authentication
 * This object is created when the protected customer starts a challenge
 * One is created for each trusted contact, and they are stored until a new challenge is created
 *
 * @param relationshipId - the id of the recovery relationship between the protected customer and the trusted contact
 * @param fullCode - this is the full recovery code - both the server part and the pake part
 * @param pakeCode - the protected customer's pake code
 * @param protectedCustomerRecoveryPakeKey - the protected customer's recovery key
 */
data class ChallengeAuthentication(
  val relationshipId: String,
  @Redacted
  val fullCode: String,
  val pakeCode: PakeCode,
  val protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
)
