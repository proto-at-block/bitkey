package build.wallet.emergencyaccesskit

import okio.ByteString

/**
 * Represents an Emergency Exit Kit data, containing the PDF bytes.
 */
data class EmergencyAccessKitData(
  val pdfData: ByteString,
)
