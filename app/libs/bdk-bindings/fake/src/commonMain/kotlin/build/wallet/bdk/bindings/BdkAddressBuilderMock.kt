package build.wallet.bdk.bindings

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bdk.bindings.BdkResult.Ok

class BdkAddressBuilderMock(
  turbine: (String) -> Turbine<Any>,
) : BdkAddressBuilder {
  var buildFromAddressReturn: BdkResult<BdkAddress> = Ok(BdkAddressMock())

  override fun build(
    address: String,
    bdkNetwork: BdkNetwork,
  ): BdkResult<BdkAddress> = buildFromAddressReturn

  val buildFromScriptCalls = turbine("build from script calls")
  var buildFromScriptReturn: BdkAddress = BdkAddressMock()

  override fun build(
    script: BdkScript,
    network: BdkNetwork,
  ): BdkResult<BdkAddress> {
    buildFromScriptCalls += script
    return Ok(buildFromScriptReturn)
  }

  fun reset() {
    buildFromScriptReturn = BdkAddressMock()
  }
}
