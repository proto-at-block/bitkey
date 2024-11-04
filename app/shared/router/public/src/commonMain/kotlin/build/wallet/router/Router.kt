package build.wallet.router

/**
 * This is a generic router for handling deep links
 */
object Router {
  /**
   The parsed route is set on our singleton object here, triggering any stored callbacks.
   */
  var route: Route? = null
    set(value) {
      if (value != null) {
        field = value
        invokeCallback()
      } else {
        field = value
      }
    }

  /**
   * This is a lambda that is called from the usage site.
   * This implementation is admittedly naive, but for our one deep link (TC Invite) it is sufficient
   * because all three callsites are unique so we don't need to worry about overwriting the callback.
   */
  private var callback: ((Route) -> Boolean)? = null

  /**
   * This function is used to navigate to a route. It takes a lambda that contains the navigation logic.
   * We only ever want to navigate to a route once, so we need to set the route to null after navigating.
   * @param to A lambda that contains the navigation logic
   */
  fun onRouteChange(to: (Route) -> Boolean) {
    callback = to
    // cold start (Route is already set before the state machine is initalized)
    route?.let {
      invokeCallback()
    }
  }

  private fun invokeCallback() {
    route?.let {
      val invoked = callback?.invoke(it)
      // Routes stay active until invoked
      if (invoked == true) {
        route = null
      }
    }
  }

  fun reset() {
    route = null
    callback = null
  }
}
