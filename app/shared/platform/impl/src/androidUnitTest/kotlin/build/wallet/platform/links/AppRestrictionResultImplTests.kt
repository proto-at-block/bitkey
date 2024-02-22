package build.wallet.platform.links

import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Failed
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppRestrictionResultImplTests : FunSpec({
  test("app restriction results success") {
    val result =
      appRestrictionResult(
        appRestrictions = AppRestrictions(packageName = "test.package", minVersion = 410000),
        packageInfo = AndroidPackageInfo(packageName = "test.package", longVersionCode = 420000)
      )
    result.shouldBe(Success)
  }

  test("app restriction results success - app versions equal") {
    val result =
      appRestrictionResult(
        appRestrictions = AppRestrictions(packageName = "test.package", minVersion = 420000),
        packageInfo = AndroidPackageInfo(packageName = "test.package", longVersionCode = 420000)
      )
    result.shouldBe(Success)
  }

  test("app restriction results failed") {
    val result =
      appRestrictionResult(
        appRestrictions = AppRestrictions(packageName = "test.package", minVersion = 420000),
        packageInfo = AndroidPackageInfo(packageName = "test.package", longVersionCode = 410000)
      )
    result.shouldBe(Failed(AppRestrictions(packageName = "test.package", minVersion = 420000)))
  }

  test("no app restriction results") {
    val result =
      appRestrictionResult(
        appRestrictions = AppRestrictions(packageName = "test.package", minVersion = 420000),
        packageInfo = AndroidPackageInfo(packageName = "test.package2", longVersionCode = 420000)
      )
    result.shouldBe(None)
  }
})
