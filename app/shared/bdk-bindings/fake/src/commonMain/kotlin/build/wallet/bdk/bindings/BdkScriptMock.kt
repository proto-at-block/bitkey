package build.wallet.bdk.bindings

class BdkScriptMock : BdkScript {
  override val rawOutputScript = emptyList<UByte>()
}
