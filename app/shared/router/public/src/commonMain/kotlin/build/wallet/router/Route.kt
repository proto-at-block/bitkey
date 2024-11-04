package build.wallet.router

import build.wallet.navigation.v1.NavigationScreenId
import io.ktor.http.*

/**
 * This is a sealed class that represents the deep link routes that the router can handle.
 */
sealed class Route {
  object SupportedPaths {
    const val TRUSTED_CONTACT = "/links/downloads/trusted-contact"
    const val APP_DEEPLINK = "/links/app"
  }

  object SupportedHosts {
    const val PROD = "bitkey.world"
    const val STAGING = "web-site.bitkeystaging.com"
  }

  object QueryParamKeys {
    const val CONTEXT = "context"
    const val EVENT = "event"
    const val EVENT_ID = "event_id"
    const val SOURCE = "source"
  }

  object Context {
    const val PARTNER_TRANSFER_REDIRECT = "partner_transfer"
    const val PARTNER_SALE_REDIRECT = "partner_sale"
  }

  object DeepLink {
    const val NAVIGATE_TO_SCREEN_ID = "navigate_to_screen_id"
  }

  companion object {
    /**
     * This function takes a string and returns a Route object if the URL is valid and matches a known route.
     * @return A Route object if the URL is valid and matches a known route, otherwise null
     */
    @Suppress("DestructuringDeclarationWithTooManyEntries")
    fun from(url: String): Route? {
      // URL deeplink
      if (url.startsWith("https", ignoreCase = true)) {
        val parsedUrl = Url(url)
        // validate https
        if (parsedUrl.protocol.name != "https") return null
        // validate expected host
        if (!(parsedUrl.host == SupportedHosts.STAGING || parsedUrl.host == SupportedHosts.PROD)) return null
        // return a valid route or null
        return when (parsedUrl.encodedPath) {
          SupportedPaths.TRUSTED_CONTACT -> {
            if (parsedUrl.fragment.isEmpty()) {
              null
            } else {
              TrustedContactInvite(parsedUrl.fragment)
            }
          }
          SupportedPaths.APP_DEEPLINK -> {
            when (parsedUrl.parameters[QueryParamKeys.CONTEXT]) {
              Context.PARTNER_TRANSFER_REDIRECT ->
                PartnerTransferDeeplink(
                  partner = parsedUrl.parameters[QueryParamKeys.SOURCE],
                  event = parsedUrl.parameters[QueryParamKeys.EVENT],
                  partnerTransactionId = parsedUrl.parameters[QueryParamKeys.EVENT_ID]
                )
              Context.PARTNER_SALE_REDIRECT -> PartnerSaleDeeplink(
                partner = parsedUrl.parameters[QueryParamKeys.SOURCE],
                event = parsedUrl.parameters[QueryParamKeys.EVENT],
                partnerTransactionId = parsedUrl.parameters[QueryParamKeys.EVENT_ID]
              )
              else -> null
            }
          }
          else -> null
        }
      } else {
        return null
      }
    }

    /*
     * This function takes a screenId and returns a Route object if the screenId is valid
     */
    fun from(screenId: Int): Route? {
      NavigationScreenId.fromValue(screenId)?.let {
        return NavigationDeeplink(it)
      }

      return null
    }
  }

  // Server-sent deeplinks

  data class NavigationDeeplink(val screen: NavigationScreenId) : Route()

  // URL deeplinks

  /**
   * This is a data class that represents the trusted contact invite route.
   * @param inviteCode The invite code for the trusted contact
   */
  data class TrustedContactInvite(val inviteCode: String) : Route()

  data class PartnerTransferDeeplink(val partner: String?, val event: String?, val partnerTransactionId: String?) : Route()

  data class PartnerSaleDeeplink(val partner: String?, val event: String?, val partnerTransactionId: String?) : Route()
}
