package build.wallet.firmware

data class CoredumpFragment(
  val data: List<UByte>,
  val offset: Int,
  val complete: Boolean,
  val coredumpsRemaining: Int,
  val mcuRole: McuRole?,
  val mcuName: McuName?,
)
