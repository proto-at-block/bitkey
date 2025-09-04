package build.wallet.router

import build.wallet.navigation.v1.NavigationScreenId
import io.ktor.http.Url

/**
 * This is a sealed class that represents the deep link routes that the router can handle.
 */
sealed class Route {
  object SupportedPaths {
    const val TRUSTED_CONTACT = "/links/downloads/trusted-contact"
    const val BENEFICIARY = "/links/downloads/beneficiary"
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
    const val PARTNER_TRANSFER_LINK = "partner_transfer_link"
    const val SCREEN = "screen"
  }

  object NotificationPayloadKeys {
    const val NAVIGATE_TO_SCREEN_ID = "navigate_to_screen_id"
    const val INHERITANCE_CLAIM_ID = "inheritance_claim_id"
    const val RECOVERY_RELATIONSHIP_ID = "recovery_relationship_id"
  }

  companion object {
    /**
     * This function takes a string and returns a Route object if the URL is valid and matches a known route.
     * @return A Route object if the URL is valid and matches a known route, otherwise null
     */
    @Suppress("DestructuringDeclarationWithTooManyEntries", "NestedBlockDepth")
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
          SupportedPaths.BENEFICIARY -> {
            if (parsedUrl.fragment.isEmpty()) {
              null
            } else {
              BeneficiaryInvite(parsedUrl.fragment)
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
              Context.PARTNER_TRANSFER_LINK ->
                PartnerTransferLinkDeeplink(
                  partner = parsedUrl.parameters[QueryParamKeys.SOURCE],
                  event = parsedUrl.parameters[QueryParamKeys.EVENT],
                  eventId = parsedUrl.parameters[QueryParamKeys.EVENT_ID]
                )
              Context.SCREEN ->
                parsedUrl.parameters[NotificationPayloadKeys.NAVIGATE_TO_SCREEN_ID]?.let {
                  NavigationScreenId.fromValue(it.toInt())?.let {
                    NavigationDeeplink(screen = it)
                  }
                }
              else -> null
            }
          }
          else -> null
        }
      } else {
        return null
      }
    }

    /**
     * This function takes a map of extras from a notification and returns a Route object if the extras match a known route.
     * @return A Route object if the extras match a known route, otherwise null
     */
    fun from(extras: Map<String, Any?>): Route? {
      val screenId = (extras[NotificationPayloadKeys.NAVIGATE_TO_SCREEN_ID] as? String)?.toIntOrNull()
      val claimId = extras[NotificationPayloadKeys.INHERITANCE_CLAIM_ID] as? String
      val recoveryRelationshipId = extras[NotificationPayloadKeys.RECOVERY_RELATIONSHIP_ID] as? String

      if (claimId != null && screenId != null) {
        NavigationScreenId.fromValue(screenId)?.let {
          return InheritanceClaimNavigationDeeplink(it, claimId)
        }
      }

      if (recoveryRelationshipId != null && screenId != null) {
        NavigationScreenId.fromValue(screenId)?.let {
          return RecoveryRelationshipNavigationDeepLink(it, recoveryRelationshipId)
        }
      }

      screenId?.let {
        NavigationScreenId.fromValue(screenId)?.let {
          return NavigationDeeplink(it)
        }
      }

      return null
    }
  }

  /*
   * Routes
   * These are the possible routes that the router can handle.
   * Each route is a data class that represents a specific deep link, either a URL or via a BE notification.
   */

  /**
   * Navigate to a screen by ID
   * @param screen The screen to navigate to
   */
  data class NavigationDeeplink(val screen: NavigationScreenId) : Route()

  /**
   * Navigate to a specified screen to perform an action based on the recovery relationship id
   * @param screen The screen to navigate to
   * @param recoveryRelationshipId The claim ID
   */
  data class RecoveryRelationshipNavigationDeepLink(
    val screen: NavigationScreenId,
    val recoveryRelationshipId: String,
  ) : Route()

  /**
   * Navigate to a specified screen to perform an action based on the claim ID
   * @param screen The screen to navigate to
   * @param claimId The claim ID
   */
  data class InheritanceClaimNavigationDeeplink(
    val screen: NavigationScreenId,
    val claimId: String,
  ) : Route()

  /**
   * Navigate to either:
   * a) Add TC screen with invite code
   * b) TC onboarding
   * @param inviteCode The invite code for the trusted contact
   */
  data class TrustedContactInvite(val inviteCode: String) : Route()

  /**
   * Navigate to either:
   * a) Become beneficiary screen with invite code
   * b) Beneficiary onboarding
   * @param inviteCode The invite code for the trusted contact
   */
  data class BeneficiaryInvite(val inviteCode: String) : Route()

  /**
   * This is a data class that represents the partner transfer deeplink route.
   * @param partner The partner name
   * @param event The event name
   * @param partnerTransactionId The partner transaction ID
   */
  data class PartnerTransferDeeplink(
    val partner: String?,
    val event: String?,
    val partnerTransactionId: String?,
  ) : Route()

  /**
   * This is a data class that represents the partner sale deeplink route.
   * @param partner The partner name
   * @param event The event name
   * @param partnerTransactionId The partner transaction ID
   */
  data class PartnerSaleDeeplink(
    val partner: String?,
    val event: String?,
    val partnerTransactionId: String?,
  ) : Route()

  /**
   * Navigates to the hardware recovery flow which initializes a new recovery
   */
  data object InitiateHardwareRecovery : Route()

  /**
   * This is a data class that represents the partner transfer link deeplink route.
   *
   * A transfer link links bitkey to an external party, typically by exporting an address
   * to that partner.
   *
   * @param partner The partner name
   * @param event The event name
   * @param eventId The tokenized secret to establish the link
   */
  data class PartnerTransferLinkDeeplink(
    val partner: String?,
    val event: String?,
    val eventId: String?,
  ) : Route()
}
