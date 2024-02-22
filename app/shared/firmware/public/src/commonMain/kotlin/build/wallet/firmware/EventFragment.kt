package build.wallet.firmware

data class EventFragment(
  val fragment: List<UByte>,
  val remainingSize: Int,
)
