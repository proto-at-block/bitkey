package build.wallet.statemachine.recovery.socrec.challenge

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class ChallengeCodeFormatterImpl : ChallengeCodeFormatter {
  override fun format(challengeCode: String): String {
    return challengeCode.hyphenateBy(4)
  }

  private fun String.hyphenateBy(size: Int): String {
    return chunked(size).joinToString("-")
  }
}
