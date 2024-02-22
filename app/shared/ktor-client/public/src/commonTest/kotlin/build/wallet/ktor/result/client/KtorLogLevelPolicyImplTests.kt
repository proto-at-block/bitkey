package build.wallet.ktor.result.client

import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Team
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.logging.LogLevel.ALL
import io.ktor.client.plugins.logging.LogLevel.INFO

class KtorLogLevelPolicyImplTests : FunSpec({

  test("for development app config") {
    KtorLogLevelPolicyImpl(Development)
      .level().shouldBe(ALL)
  }

  test("for internal team app config") {
    KtorLogLevelPolicyImpl(Team)
      .level().shouldBe(INFO)
  }

  test("for customer app config") {
    KtorLogLevelPolicyImpl(Customer)
      .level().shouldBe(INFO)
  }
})
