package build.wallet.emergencyexitkit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

/**
 * [EmergencyExitKitPdfGenerator] is responsible for generating an [EmergencyExitKitData].
 * This uses a Template PDF that is annotated at runtime by text, links, and images.
 *
 * Current Template PDF design: <https://www.figma.com/file/dSOfdY6Rb14R8zoHVWUV5D/Break-Glass?type=design&node-id=173-6903&mode=design&t=eAgWOEPJ73mOK45u-4>
 */
interface EmergencyExitKitPdfGenerator {
  /**
   * Generates an [EmergencyExitKitData] populated with appropriate values at runtime.
   *
   * Returns the [EmergencyExitKitData] if generation succeeded, otherwise an error.
   */
  suspend fun generate(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyExitKitData, Error>
}
