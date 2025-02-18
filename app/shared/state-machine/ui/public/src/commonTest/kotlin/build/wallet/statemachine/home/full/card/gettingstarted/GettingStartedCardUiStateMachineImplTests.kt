package build.wallet.statemachine.home.full.card.gettingstarted

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_GETTINGSTARTED_COMPLETED
import build.wallet.analytics.v1.Action.ACTION_APP_WALLET_FUNDED
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.InternetUnreachable
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.relationships.Relationships
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.*
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.limit.MobilePayEnabledDataMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Height
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Scale
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachineImpl
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant

class GettingStartedCardUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val onAddBitcoinCalls = turbines.create<Unit>("add bitcoin calls")
  val onEnableSpendingLimitCalls = turbines.create<Unit>("enable spending limit calls")
  val onInviteTrustedContactCalls = turbines.create<Unit>("invite trusted contact calls")
  val onAddAdditionalFingerprintCalls = turbines.create<Unit>("add additional fingerprint calls")

  val appFunctionalityService = AppFunctionalityServiceFake()
  val gettingStartedTaskDao =
    GettingStartedTaskDaoMock(
      turbine = turbines::create
    )

  val bitcoinWalletService = BitcoinWalletServiceFake()
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val socRecService = SocRecServiceFake()

  val props =
    GettingStartedCardUiProps(
      onAddBitcoin = { onAddBitcoinCalls += Unit },
      onEnableSpendingLimit = { onEnableSpendingLimitCalls += Unit },
      onInviteTrustedContact = { onInviteTrustedContactCalls += Unit },
      onAddAdditionalFingerprint = { onAddAdditionalFingerprintCalls += Unit },
      onShowAlert = {},
      onDismissAlert = {}
    )

  val stateMachine =
    GettingStartedCardUiStateMachineImpl(
      appFunctionalityService = appFunctionalityService,
      gettingStartedTaskDao = gettingStartedTaskDao,
      eventTracker = eventTracker,
      bitcoinWalletService = bitcoinWalletService,
      mobilePayService = mobilePayService,
      socRecService = socRecService
    )

  beforeTest {
    gettingStartedTaskDao.reset()
    bitcoinWalletService.reset()
    mobilePayService.reset()
    appFunctionalityService.reset()
    socRecService.reset()

    socRecService.socRecRelationships.value = Relationships.EMPTY
  }

  test("card model should be null") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(listOf())
    }
  }

  test("add one completed task") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )
      awaitItem().shouldNotBeNull().expect(
        tasks = listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )
    }
  }

  test("onAddBitcoin click") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )
      cardModel.onClick("Add bitcoin").invoke()
      onAddBitcoinCalls.awaitItem()
    }
  }

  test("onEnableSpendingLimit click") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(EnableSpendingLimit, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(EnableSpendingLimit, state = Incomplete))
      )
      cardModel.onClick("Customize transfer settings").invoke()
      onEnableSpendingLimitCalls.awaitItem()
    }
  }

  test("onInviteTrustedContact click") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(InviteTrustedContact, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(InviteTrustedContact, state = Incomplete))
      )
      cardModel.onClick("Invite a Trusted Contact").invoke()
      onInviteTrustedContactCalls.awaitItem()
    }
  }

  test("onAddAdditionalFingerprint click") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddAdditionalFingerprint, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(AddAdditionalFingerprint, state = Incomplete))
      )
      cardModel.onClick("Add additional fingerprint").invoke()
      onAddAdditionalFingerprintCalls.awaitItem()
    }
  }

  test("complete all tasks") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()

      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(InviteTrustedContact, state = Incomplete),
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete),
          GettingStartedTask(AddAdditionalFingerprint, state = Incomplete)
        )
      )
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Incomplete),
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(AddAdditionalFingerprint, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(InviteTrustedContact, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(AddAdditionalFingerprint, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(AddAdditionalFingerprint, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(EnableSpendingLimit, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Complete),
            GettingStartedTask(AddAdditionalFingerprint, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(AddAdditionalFingerprint, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Complete),
            GettingStartedTask(AddAdditionalFingerprint, state = Complete)
          )
      )

      // And then animate
      awaitItem().shouldNotBeNull().animation
        .shouldContainExactly(
          AnimationSet(setOf(Scale(1.05f)), 0.55),
          AnimationSet(setOf(Scale(0.001f), Height(0f)), 0.55)
        )

      // And then clear the dao
      gettingStartedTaskDao.clearTasksCalls.awaitItem()
      awaitItem().shouldBeNull()
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_GETTINGSTARTED_COMPLETED)
      )
    }
  }

  test("EnableSpendingLimit task listener") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete)
        )
      )

      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      mobilePayService.mobilePayData.value = MobilePayEnabledDataMock

      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Complete)
          )
      )
    }
  }

  test("AddBitcoin task listener") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(EnableSpendingLimit, state = Incomplete),
          GettingStartedTask(AddBitcoin, state = Incomplete)
        )
      )

      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(AddBitcoin, state = Incomplete)
          )
      )

      bitcoinWalletService.transactionsData.value = TransactionsDataMock

      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(AddBitcoin, state = Complete)
          )
      )
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_WALLET_FUNDED))
    }
  }

  test("InviteTrustedContact task listener") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(EnableSpendingLimit, state = Incomplete),
          GettingStartedTask(InviteTrustedContact, state = Incomplete)
        )
      )

      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(InviteTrustedContact, state = Incomplete)
          )
      )

      socRecService.socRecRelationships.value = RelationshipsFake

      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(InviteTrustedContact, state = Complete)
          )
      )
    }
  }

  test("Tasks disabled in limited functionality") {
    appFunctionalityService.status.value = AppFunctionalityStatus.LimitedFunctionality(
      cause = InternetUnreachable(
        lastReachableTime = Instant.DISTANT_PAST,
        lastElectrumSyncReachableTime = Instant.DISTANT_PAST
      )
    )
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete),
          GettingStartedTask(InviteTrustedContact, state = Incomplete),
          GettingStartedTask(AddAdditionalFingerprint, state = Incomplete)
        )
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expectTaskModelWithEnabled(
        taskPairs =
          listOf(
            Pair(GettingStartedTask(AddBitcoin, state = Incomplete), false),
            Pair(GettingStartedTask(EnableSpendingLimit, state = Incomplete), false),
            Pair(GettingStartedTask(InviteTrustedContact, state = Incomplete), false),
            Pair(GettingStartedTask(AddAdditionalFingerprint, state = Incomplete), true)
          )
      )
    }
  }
})

/**
 * Helper function to check card model for drill row content
 */
private fun CardModel.expect(tasks: List<GettingStartedTask>) =
  expectTaskModelWithEnabled(taskPairs = tasks.map { Pair(it, true) })

/**
 * Helper function to check card model for drill row content
 * The task is paired with whether it should be enabled
 */
private fun CardModel.expectTaskModelWithEnabled(
  taskPairs: List<Pair<GettingStartedTask, Boolean>>,
) {
  shouldHaveTitle("Getting Started")
  subtitle.shouldBeNull()
  leadingImage.shouldBeNull()
  val drillList = content.shouldBeInstanceOf<DrillList>().items
  for ((i, taskPair) in taskPairs.withIndex()) {
    val (task, taskEnabled) = taskPair
    drillList[i].enabled.shouldBe(taskEnabled)
    drillList[i].leadingAccessory.shouldNotBeNull()
      .shouldBeTypeOf<ListItemAccessory.IconAccessory>()
      .model.iconImage.shouldBeTypeOf<IconImage.LocalImage>()
      .icon.shouldBe(
        when (task.state) {
          Complete -> SmallIconCheckFilled
          Incomplete ->
            when (task.id) {
              EnableSpendingLimit -> SmallIconMobileLimit
              AddBitcoin -> SmallIconPlusStroked
              InviteTrustedContact -> SmallIconShieldPerson
              AddAdditionalFingerprint -> SmallIconFingerprint
            }
        }
      )
  }
}

/** Helper function to get onClick action for drill row */
private fun CardModel.onClick(taskTitle: String): (() -> Unit) {
  return content.shouldBeInstanceOf<DrillList>()
    .items.first { it.title == taskTitle }
    .onClick.shouldNotBeNull()
}
