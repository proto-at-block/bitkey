package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class EmergencyAccessKitPdfGeneratorFake : EmergencyAccessKitPdfGenerator {
  override suspend fun generate(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyAccessKitData, Error> {
    val fakeEmergencyAccessKitData = EmergencyAccessKitData(ByteString.EMPTY)
    return Ok(fakeEmergencyAccessKitData)
  }
}
