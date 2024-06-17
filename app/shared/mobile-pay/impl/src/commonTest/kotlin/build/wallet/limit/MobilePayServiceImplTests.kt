package build.wallet.limit

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClientMock
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.firstOrNull

class MobilePayServiceImplTests : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)
  val spendingLimitDao = SpendingLimitDaoFake()
  val spendingLimitF8eClient = MobilePaySpendingLimitF8eClientMock()
  val mobilePayStatusProvider = MobilePayStatusRepositoryMock(turbines::create)
  val mobilePayService =
    MobilePayServiceImpl(eventTracker, spendingLimitDao, spendingLimitF8eClient, mobilePayStatusProvider)

  beforeTest {
    spendingLimitDao.reset()
  }

  val hwPop = HwFactorProofOfPossession("")

  test("enable mobile pay by setting the limit for the first time") {
    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock, hwPop).shouldBeOk()

    // verify that the active spending limit was set
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(SpendingLimitMock)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )
  }

  test("set the same limit again") {
    // prepopulate database
    spendingLimitDao.saveAndSetSpendingLimit(SpendingLimitMock)

    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock, hwPop).shouldBeOk()

    // verify that the active spending limit was updated
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(SpendingLimitMock)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )
  }

  test("update mobile pay limit") {
    // prepopulate database
    spendingLimitDao.saveAndSetSpendingLimit(SpendingLimitMock)

    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock2, hwPop).shouldBeOk()

    // verify that the active spending limit was updated
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(SpendingLimitMock2)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock2)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )
  }

  test("disable mobile pay when it's enabled") {
    // prepopulate database
    spendingLimitDao.saveAndSetSpendingLimit(SpendingLimitMock)

    mobilePayService.disable(account = FullAccountMock).shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }

  /**
   * Test that mobile pay is disabled when it's already disabled (there is no active spending limit).
   * This is an edge case that should not be reproducible in UI, but is tested for completeness.
   */
  test("disable mobile pay when it's disabled") {
    mobilePayService.disable(account = FullAccountMock).shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(null)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }

  test("enable and then disable mobile pay") {
    // setup
    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock, hwPop).shouldBeOk()
    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )

    mobilePayService.disable(account = FullAccountMock).shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }
})
