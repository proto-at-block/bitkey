package bitkey.bugsnag

import co.touchlab.crashkios.bugsnag.BugsnagKotlin

actual fun bugsnagSetCustomValue(
  section: String,
  key: String,
  value: String,
) {
  BugsnagKotlin.setCustomValue(section, key, value)
}
