package build.wallet.bitkey.relationships

import build.wallet.bitkey.socrec.SocialChallenge

/***
 * A wrapper that contains both the challenge and all the challenge authentications that we've
 * generated for the challenge (one for each RC)
 * @param challenge - the social challenge object
 * @param tcAuths - the list of Recovery Contact challenge authentications
 *
 */
data class ChallengeWrapper(
  val challenge: SocialChallenge,
  val tcAuths: List<ChallengeAuthentication>,
)
