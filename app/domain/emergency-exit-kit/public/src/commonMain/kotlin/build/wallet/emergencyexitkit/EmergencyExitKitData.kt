package build.wallet.emergencyexitkit

import okio.ByteString

/**
 * Represents an Emergency Exit Kit data, containing the PDF bytes.
 */
data class EmergencyExitKitData(
  val pdfData: ByteString,
)
