package build.wallet.bdk

import build.wallet.bdk.bindings.BdkMnemonic
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkMnemonicWordCount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BdkMnemonicGeneratorImpl : BdkMnemonicGenerator {
  override fun generateMnemonicBlocking(wordCount: BdkMnemonicWordCount): BdkMnemonic {
    when (wordCount) {
      BdkMnemonicWordCount.WORDS_24 -> FfiWordCount.WORDS24
    }
    return BdkMnemonicImpl(
      ffiMnemonic = FfiMnemonic(wordCount = wordCount.ffiWordCount)
    )
  }

  override fun fromStringBlocking(mnemonic: String): BdkMnemonic {
    return BdkMnemonicImpl(FfiMnemonic.fromString(mnemonic))
  }
}
