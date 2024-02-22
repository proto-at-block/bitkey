package build.wallet.bdk

import build.wallet.bdk.bindings.BdkScript

internal data class BdkScriptImpl(
  val ffiScript: FfiScript,
) : BdkScript {
  override fun rawOutputScript(): List<UByte> = ffiScript.toBytes()
}
