package build.wallet.platform.config

data class DeviceTokenConfig(
  val deviceToken: String,
  val touchpointPlatform: TouchpointPlatform,
)

enum class TouchpointPlatform(val platform: String) {
  Apns("Apns"), // TODO: remove after old apps are gone [W-4150]
  ApnsCustomer("ApnsCustomer"),
  ApnsTeam("ApnsTeam"),
  ApnsTeamAlpha("ApnsTeamAlpha"),
  Fcm("Fcm"), // TODO: remove after old apps are gone [W-4150]
  FcmCustomer("FcmCustomer"),
  FcmTeam("FcmTeam"),
}
