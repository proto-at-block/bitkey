package build.wallet.bugsnag

import co.touchlab.crashkios.bugsnag.BugsnagConfiguration
import co.touchlab.crashkios.bugsnag.startBugsnag

/**
 * iOS Bugsnag initializer using bindings from CrashKiOS.
 *
 * This should be called by iOS app as soon as possible to start catching crashes.
 *
 * This will pipe KMP exceptions through CrashKiOS to Bugsnag. CrashKiOS makes sure that unhandled
 * KMP exceptions thrown in iOS code are reported with the correct stack trace:
 * https://crashkios.touchlab.co/docs/misc/THE_PROBLEM
 */
object Bugsnag {
  fun initialize(config: BugsnagConfiguration) {
    startBugsnag(config)
  }
}
