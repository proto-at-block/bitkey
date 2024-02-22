package build.wallet.bitcoin.address

enum class Encoding {
  BECH32,
  BECH32M,
}

internal data class Bech32Data(
  val encoding: Encoding,
  val humanReadablePart: String,
  val data: ByteArray,
)
