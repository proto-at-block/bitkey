package build.wallet.router

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class RouterTests : FunSpec({

  val stagingRoute = "https://web-site.bitkeystaging.com/links/downloads/trusted-contact#1234"
  val prodRoute = "https://bitkey.world/links/downloads/trusted-contact#1234"

  test("http routes fail") {
    Route.fromUrl(
      "http://web-site.bitkeystaging.com/links/downloads/trusted-contact#1234"
    ).shouldBeNull()
  }

  test("incorrect host fails") {
    Route.fromUrl("http://google.com/links/downloads/trusted-contact#1234").shouldBeNull()
  }

  test("incorrect path fails") {
    Route.fromUrl("http://google.com/links/dowoads/trusted-contact#1234").shouldBeNull()
  }

  test("invite trusted contact route") {
    Route.fromUrl(stagingRoute).shouldNotBeNull().shouldBe(Route.TrustedContactInvite("1234"))
    Route.fromUrl(prodRoute).shouldNotBeNull().shouldBe(Route.TrustedContactInvite("1234"))
  }

  test("navigate to route") {
    Router.onRouteChange {
      it.shouldBeEqual(Route.TrustedContactInvite("1234"))
      return@onRouteChange true
    }
    Router.route = Route.fromUrl(stagingRoute)
    Router.route.shouldBeNull()
  }

  test("deferred navigate to route") {
    Router.onRouteChange {
      it.shouldBeEqual(Route.TrustedContactInvite("1234"))
      return@onRouteChange false
    }
    Router.route = Route.fromUrl(stagingRoute)
    Router.route.shouldNotBeNull()
  }
})
