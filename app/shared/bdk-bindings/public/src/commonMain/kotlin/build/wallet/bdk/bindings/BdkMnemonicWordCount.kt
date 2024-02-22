package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L111
 *
 * We only use 24 words.
 */
enum class BdkMnemonicWordCount {
  WORDS_24,
}
