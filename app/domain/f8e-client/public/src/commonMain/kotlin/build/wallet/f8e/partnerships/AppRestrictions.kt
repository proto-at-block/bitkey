package build.wallet.f8e.partnerships
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A struct representing the restrictions of the partner app. This struct is used to determine
 * if sufficient version of partner app installed if the redirect type is DEEPLINK
 *
 * @property packageName represents the package name
 * @property minVersion represents the minimum version that supports the integration
 */
@Serializable
data class AppRestrictions(
  @SerialName("package_name")
  val packageName: String,
  @SerialName("min_version")
  val minVersion: Long,
)
