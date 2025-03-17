package build.wallet.statemachine.recovery.socrec.challenge

/** Adds dashes to the challenge code. */
interface ChallengeCodeFormatter {
  /** Return a challenge code with dashes. */
  fun format(challengeCode: String): String
}
