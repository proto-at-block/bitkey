package build.wallet.bugsnag

import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Team
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BugsnagConfigTests : FunSpec({
  test("create config - development") {
    BugsnagConfig(appVariant = Development).shouldBe(
      BugsnagConfig(
        releaseStage = "development"
      )
    )
  }

  test("create config - internal team") {
    BugsnagConfig(appVariant = Team).shouldBe(
      BugsnagConfig(
        releaseStage = "team"
      )
    )
  }

  test("create config - internal beta") {
    BugsnagConfig(appVariant = Customer).shouldBe(
      BugsnagConfig(
        releaseStage = "customer"
      )
    )
  }
})
