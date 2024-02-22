package build.wallet.bdk

import build.wallet.bdk.bindings.BdkMnemonic

internal class BdkMnemonicImpl(
  val ffiMnemonic: FfiMnemonic,
) : BdkMnemonic {
  override val words: String get() = ffiMnemonic.asString()
}
