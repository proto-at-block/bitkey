package build.wallet.f8e.partnerships
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A struct representing the redirect information needed to present the partner experience
 *
 * @property appRestrictions represents the restrictions of the partner app
 * @property url represents the URL of the partner experience
 * @property redirectType represents the type of the URL which dictates how the redirect should be
 * handled
 */
@Serializable
data class RedirectInfo(
  @SerialName("app_restrictions")
  val appRestrictions: AppRestrictions?,
  val url: String,
  @SerialName("redirect_type")
  val redirectType: RedirectUrlType,
)

/**
 * An enum representing possible URL types for handling partner experience
 */
@Serializable
enum class RedirectUrlType {
  /**
   * DEEPLINK should result in the URL being used for application redirect
   */
  DEEPLINK,

  /**
   * WIDGET should result in the URL being displayed through as a widget through an embedded
   * web view.
   */
  WIDGET,
}
