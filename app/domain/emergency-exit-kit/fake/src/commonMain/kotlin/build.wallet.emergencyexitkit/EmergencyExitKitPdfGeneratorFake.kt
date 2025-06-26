package build.wallet.emergencyexitkit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class EmergencyExitKitPdfGeneratorFake : EmergencyExitKitPdfGenerator {
  override suspend fun generate(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyExitKitData, Error> {
    val fakeEmergencyExitKitData = EmergencyExitKitData(ByteString.EMPTY)
    return Ok(fakeEmergencyExitKitData)
  }
}
