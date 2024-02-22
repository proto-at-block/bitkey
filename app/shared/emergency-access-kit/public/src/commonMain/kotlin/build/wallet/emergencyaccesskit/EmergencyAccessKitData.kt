package build.wallet.emergencyaccesskit

import okio.ByteString

/**
 * Represents an emergency access kit data, containing the PDF bytes.
 */
data class EmergencyAccessKitData(
  val pdfData: ByteString,
)
