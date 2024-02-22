package build.wallet.bdk.bindings

class BdkScriptMock : BdkScript {
  override fun rawOutputScript(): List<UByte> = emptyList()
}
