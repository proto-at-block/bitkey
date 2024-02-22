package build.wallet.platform.hardware

data class SerialNumberComponents(
  // LMMMMRRF part of the serial number
  val model: String? = null,
  // YWW part of the serial number
  val manufactureInfo: String? = null,
)
