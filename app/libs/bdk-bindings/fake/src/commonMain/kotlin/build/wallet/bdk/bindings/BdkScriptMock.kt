package build.wallet.bdk.bindings

data class BdkScriptMock(
  override val rawOutputScript: List<UByte> = emptyList(),
) : BdkScript
