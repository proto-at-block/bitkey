package build.wallet.bitkey.socrec

/***
 * A wrapper that contains both the challenge and all the challenge authentications that we've
 * generated for the challenge (one for each TC)
 * @param challenge - the social challenge object
 * @param tcAuths - the list of trusted contact challenge authentications
 *
 */
data class ChallengeWrapper(
  val challenge: SocialChallenge,
  val tcAuths: List<ChallengeAuthentication>,
)
