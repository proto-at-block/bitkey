package build.wallet.f8e.notifications

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

class RegisterWatchAddressF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : RegisterWatchAddressF8eClient {
  val registerCalls = turbine("register calls")
  var registerReturn: Result<Unit, Error>? = null

  override suspend fun register(
    addressAndKeysetIds: List<AddressAndKeysetId>,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    registerCalls += addressAndKeysetIds
    return registerReturn!!
  }
}
