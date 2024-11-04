package build.wallet.router

import build.wallet.navigation.v1.NavigationScreenId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class RouterTests : DescribeSpec({

  describe("Route.from") {
    it(" fails for http routes") {
      Route.from(
        "http://web-site.bitkeystaging.com/links/downloads/trusted-contact#1234"
      ).shouldBeNull()
    }

    it("fails for incorrect host") {
      Route.from("http://google.com/links/downloads/trusted-contact#1234").shouldBeNull()
    }

    it("fails for incorrect path") {
      Route.from("http://google.com/links/dowoads/trusted-contact#1234").shouldBeNull()
    }

    it("deeplink") {
      Route.from(1)
        .shouldBe(Route.NavigationDeeplink(NavigationScreenId.NAVIGATION_SCREEN_ID_MONEY_HOME))
    }

    describe("trusted contact routing") {
      val stagingRoute = "https://web-site.bitkeystaging.com/links/downloads/trusted-contact#1234"
      val prodRoute = "https://bitkey.world/links/downloads/trusted-contact#1234"

      it("works for invite trusted contact route") {
        Route.from(stagingRoute).shouldNotBeNull().shouldBe(Route.TrustedContactInvite("1234"))
        Route.from(prodRoute).shouldNotBeNull().shouldBe(Route.TrustedContactInvite("1234"))
      }
    }

    describe("app deeplink routing") {
      it("matches APP_DEEPLINK path and has correct CONTEXT") {
        val stagingRoute = Route.from("https://web-site.bitkeystaging.com/links/app?context=partner_transfer&source=test_partner&event=test_event&event_id=test_id")
        val prodRoute = Route.from("https://bitkey.world/links/app?context=partner_transfer&source=test_partner&event=test_event&event_id=test_id")
        stagingRoute.shouldNotBeNull().shouldBe(
          Route.PartnerTransferDeeplink(
            partner = "test_partner",
            event = "test_event",
            partnerTransactionId = "test_id"
          )
        )
        prodRoute.shouldNotBeNull().shouldBe(
          Route.PartnerTransferDeeplink(
            partner = "test_partner",
            event = "test_event",
            partnerTransactionId = "test_id"
          )
        )
      }

      it("matches APP_DEEPLINK path but has unknown CONTEXT") {
        val route = Route.from("https://bitkey.world/links/app?context=other_context")
        route.shouldBe(null)
      }

      it("parses Sale link") {
        val stagingRoute = Route.from("https://web-site.bitkeystaging.com/links/app?context=partner_sale&source=test_partner&event=test_event&event_id=test_id")
        val prodRoute = Route.from("https://bitkey.world/links/app?context=partner_sale&source=test_partner&event=test_event&event_id=test_id")
        stagingRoute.shouldNotBeNull().shouldBe(
          Route.PartnerSaleDeeplink(
            partner = "test_partner",
            event = "test_event",
            partnerTransactionId = "test_id"
          )
        )
        prodRoute.shouldNotBeNull().shouldBe(
          Route.PartnerSaleDeeplink(
            partner = "test_partner",
            event = "test_event",
            partnerTransactionId = "test_id"
          )
        )
      }
    }
  }

  describe("Router.onRouteChange") {
    val route = "https://web-site.bitkeystaging.com/links/downloads/trusted-contact#1234"
    it("handles navigating to route") {
      Router.onRouteChange {
        it.shouldBeEqual(Route.TrustedContactInvite("1234"))
        return@onRouteChange true
      }
      Router.route = Route.from(route)
      Router.route.shouldBeNull()
    }

    it("handles deferred navigating to route") {
      Router.onRouteChange {
        it.shouldBeEqual(Route.TrustedContactInvite("1234"))
        return@onRouteChange false
      }
      Router.route = Route.from(route)
      Router.route.shouldNotBeNull()
    }
  }
})
