package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FullAccountFieldsCreatorMock : FullAccountFieldsCreator {
  var createResult: Result<FullAccountFields, FullAccountFieldsCreationError> =
    Ok(FullAccountFieldsMock)

  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    trustedContacts: List<TrustedContact>,
  ): Result<FullAccountFields, FullAccountFieldsCreationError> {
    return createResult
  }

  fun reset() {
    createResult = Ok(FullAccountFieldsMock)
  }
}
