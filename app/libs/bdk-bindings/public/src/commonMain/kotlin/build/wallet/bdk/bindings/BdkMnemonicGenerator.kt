package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L288
 */
interface BdkMnemonicGenerator {
  fun generateMnemonicBlocking(wordCount: BdkMnemonicWordCount): BdkMnemonic

  fun fromStringBlocking(mnemonic: String): BdkMnemonic
}

suspend fun BdkMnemonicGenerator.generateMnemonic(wordCount: BdkMnemonicWordCount): BdkMnemonic {
  return withContext(Dispatchers.BdkIO) {
    generateMnemonicBlocking(wordCount)
  }
}

suspend fun BdkMnemonicGenerator.fromString(mnemonic: String): BdkMnemonic {
  return withContext(Dispatchers.BdkIO) {
    fromStringBlocking(mnemonic)
  }
}
