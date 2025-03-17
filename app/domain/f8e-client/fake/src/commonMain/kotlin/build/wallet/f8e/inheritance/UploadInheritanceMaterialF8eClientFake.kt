package build.wallet.f8e.inheritance

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UploadInheritanceMaterialF8eClientFake(
  val uploadCalls: Turbine<InheritanceMaterial>,
  var uploadResponse: Result<Unit, Throwable> = Ok(Unit),
) : UploadInheritanceMaterialF8eClient {
  override suspend fun uploadInheritanceMaterial(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    inheritanceMaterial: InheritanceMaterial,
  ): Result<Unit, Throwable> {
    uploadCalls.add(inheritanceMaterial)
    return uploadResponse
  }
}
