package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L288
 */
interface BdkMnemonicGenerator {
  fun generateMnemonicBlocking(wordCount: BdkMnemonicWordCount): BdkMnemonic

  fun fromString(mnemonic: String): BdkMnemonic
}

suspend fun BdkMnemonicGenerator.generateMnemonic(wordCount: BdkMnemonicWordCount): BdkMnemonic {
  return withContext(Dispatchers.BdkIO) {
    generateMnemonicBlocking(wordCount)
  }
}
