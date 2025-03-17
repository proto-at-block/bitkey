package build.wallet.f8e.inheritance

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Uploads inheritance material to the server for storage and use with
 * inheritance features for Trusted Contacts.
 */
interface UploadInheritanceMaterialF8eClient {
  /**
   * Upload a collection encrypted inheritance material packages to the backend.
   */
  suspend fun uploadInheritanceMaterial(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    inheritanceMaterial: InheritanceMaterial,
  ): Result<Unit, Throwable>
}
