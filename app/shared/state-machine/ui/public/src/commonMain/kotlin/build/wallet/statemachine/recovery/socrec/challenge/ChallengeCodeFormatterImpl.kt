package build.wallet.statemachine.recovery.socrec.challenge

class ChallengeCodeFormatterImpl : ChallengeCodeFormatter {
  override fun format(challengeCode: String): String {
    return challengeCode.hyphenateBy(4)
  }

  private fun String.hyphenateBy(size: Int): String {
    return chunked(size).joinToString("-")
  }
}
