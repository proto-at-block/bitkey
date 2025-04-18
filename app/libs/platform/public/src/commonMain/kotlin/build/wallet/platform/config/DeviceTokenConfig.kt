package build.wallet.platform.config

data class DeviceTokenConfig(
  val deviceToken: String,
  val touchpointPlatform: TouchpointPlatform,
)

enum class TouchpointPlatform(val platform: String) {
  ApnsCustomer("ApnsCustomer"),
  ApnsTeam("ApnsTeam"),
  ApnsTeamAlpha("ApnsTeamAlpha"),
  FcmCustomer("FcmCustomer"),
  FcmTeam("FcmTeam"),
}
