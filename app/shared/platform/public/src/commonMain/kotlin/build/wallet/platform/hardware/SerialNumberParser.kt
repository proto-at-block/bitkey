package build.wallet.platform.hardware

interface SerialNumberParser {
  /**
   * Extracts LMMMMRRF and YWW parts of serial number to SerialNumberComponents data class
   */
  fun parse(serialNumber: String?): SerialNumberComponents
}
