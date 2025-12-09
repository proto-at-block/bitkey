package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkScript

internal data class BdkScriptImpl(
  override val rawOutputScript: List<UByte>,
) : BdkScript
