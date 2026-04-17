package build.wallet.statemachine.home.full.card.gettingstarted

import app.cash.turbine.plusAssign
import bitkey.relationships.Relationships
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
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.limit.MobilePayEnabledDataMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.recovery.socrec.SocRecServiceFake
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Height
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Scale
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.statemachine.moneyhome.card.CardModel.GettingStartedTileModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachineImpl
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconTint
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
  val onUpdateFirmwareCalls = turbines.create<Unit>("update firmware calls")
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
      onUpdateFirmware = { onUpdateFirmwareCalls += Unit },
      showUpdateFirmwareTile = false,
      onShowAlert = {},
      onDismissAlert = {}
    )

  val stateMachine =
    GettingStartedCardUiStateMachineImpl(
      appFunctionalityService = appFunctionalityService,
      gettingStartedTaskDao = gettingStartedTaskDao,
      eventTracker = eventTracker,
      bitcoinWalletService = bitcoinWalletService,
      mobilePayService = mobilePayService
    )

  beforeTest {
    gettingStartedTaskDao.reset()
    bitcoinWalletService.reset()
    mobilePayService.reset()
    appFunctionalityService.reset()
    socRecService.reset()

    socRecService.socRecRelationships.value = Relationships.EMPTY
  }

  test("cards") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete)
        )
      )
      awaitItem().shouldNotBeNull().expect(
        listOf(
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete)
        )
      )
    }
  }

  test("card model should be null") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(listOf())
    }
  }

  test("add one completed task") {
    stateMachine.test(props) {
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
    stateMachine.test(props) {
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
    stateMachine.test(props) {
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

  test("shows firmware update tile first when available") {
    stateMachine.test(props.copy(showUpdateFirmwareTile = true)) {
      val firmwareOnlyCardModel = awaitItem().shouldNotBeNull()
      firmwareOnlyCardModel.firmwareListItem().let { firmwareItem ->
        firmwareItem.title.shouldBe("Update firmware")
        firmwareItem.enabled.shouldBe(true)
        firmwareItem.leadingAccessory.shouldNotBeNull()
          .shouldBeTypeOf<ListItemAccessory.IconAccessory>()
          .model.iconImage.shouldBeTypeOf<IconImage.LocalImage>()
          .icon.shouldBe(SmallIconBitkey)
      }

      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )

      val firmwareTile = cardModel.kind
        .shouldBeTypeOf<CardModel.Kind.GettingStarted>()
        .tiles
        .first()
      firmwareTile.id.shouldBe(GettingStartedTileModel.Id.UpdateFirmware)
      firmwareTile.title.shouldBe("Update firmware")
      firmwareTile.isEnabled.shouldBe(true)
      firmwareTile.isComplete.shouldBe(false)
      firmwareTile.leadingIcon.shouldNotBeNull()
        .iconImage.shouldBeTypeOf<IconImage.LocalImage>()
        .icon.shouldBe(DotBitkey)
    }
  }

  test("onUpdateFirmware click") {
    stateMachine.test(props.copy(showUpdateFirmwareTile = true)) {
      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.firmwareTile().onClick.shouldNotBeNull().invoke()
      onUpdateFirmwareCalls.awaitItem()
      cardModel.firmwareListItem().onClick.shouldNotBeNull().invoke()
      onUpdateFirmwareCalls.awaitItem()
    }
  }

  test("keeps firmware update card after onboarding tasks clear") {
    stateMachine.test(props.copy(showUpdateFirmwareTile = true)) {
      awaitItem().shouldNotBeNull().firmwareTile()

      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )
      awaitItem().shouldNotBeNull()

      gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
      awaitItem().shouldNotBeNull()

      gettingStartedTaskDao.clearTasksCalls.awaitItem()

      val firmwareOnlyCard = awaitItem().shouldNotBeNull()
      firmwareOnlyCard.animation.shouldBeNull()
      firmwareOnlyCard.content.shouldBeInstanceOf<DrillList>()
        .items
        .single()
        .title
        .shouldBe("Update firmware")
      firmwareOnlyCard.kind.shouldBeTypeOf<CardModel.Kind.GettingStarted>()
        .tiles
        .single()
        .id
        .shouldBe(GettingStartedTileModel.Id.UpdateFirmware)
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_GETTINGSTARTED_COMPLETED)
      )
    }
  }

  test("complete all tasks") {
    stateMachine.test(props) {
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

      gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(EnableSpendingLimit, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Complete)
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
    stateMachine.test(props) {
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
    stateMachine.test(props) {
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

  test("Tasks disabled in limited functionality") {
    appFunctionalityService.status.value = AppFunctionalityStatus.LimitedFunctionality(
      cause = InternetUnreachable(
        lastReachableTime = Instant.DISTANT_PAST,
        lastElectrumSyncReachableTime = Instant.DISTANT_PAST
      )
    )
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete)
        )
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expectTaskModelWithEnabled(
        taskPairs =
          listOf(
            Pair(GettingStartedTask(AddBitcoin, state = Incomplete), false),
            Pair(GettingStartedTask(EnableSpendingLimit, state = Incomplete), false)
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
  val gettingStartedKind = kind.shouldBeTypeOf<CardModel.Kind.GettingStarted>()
  val drillList = content.shouldBeInstanceOf<DrillList>().items
  for (taskPair in taskPairs) {
    val (task, taskEnabled) = taskPair
    val listItem = drillList.first { it.title == task.listTitle() }
    listItem.enabled.shouldBe(taskEnabled)
    listItem.leadingAccessory.shouldNotBeNull()
      .shouldBeTypeOf<ListItemAccessory.IconAccessory>()
      .model.iconImage.shouldBeTypeOf<IconImage.LocalImage>()
      .icon.shouldBe(
        when (task.state) {
          Complete -> SmallIconCheckFilled
          Incomplete ->
            when (task.id) {
              EnableSpendingLimit -> SmallIconMobileLimit
              AddBitcoin -> SmallIconPlusStroked
            }
        }
      )

    val tile = gettingStartedKind.tiles.first {
      it.id ==
        when (task.id) {
          AddBitcoin -> GettingStartedTileModel.Id.AddBitcoin
          EnableSpendingLimit -> GettingStartedTileModel.Id.EnableSpendingLimit
        }
    }
    tile.id.shouldBe(
      when (task.id) {
        AddBitcoin -> GettingStartedTileModel.Id.AddBitcoin
        EnableSpendingLimit -> GettingStartedTileModel.Id.EnableSpendingLimit
      }
    )
    tile.title.shouldBe(listItem.title)
    tile.isEnabled.shouldBe(taskEnabled || task.state == Complete)
    tile.isComplete.shouldBe(task.state == Complete)
    tile.leadingIcon.shouldNotBeNull()
      .iconImage.shouldBeTypeOf<IconImage.LocalImage>()
      .icon.shouldBe(
        when (task.state) {
          Complete -> SmallIconCheckFilled
          Incomplete ->
            when (task.id) {
              AddBitcoin -> DotCoins
              EnableSpendingLimit -> DotPair
            }
        }
      )
    tile.leadingIcon?.iconTint.shouldBe(
      when (task.state) {
        Complete -> IconTint.On60
        Incomplete ->
          when (taskEnabled) {
            true -> null
            false -> IconTint.On30
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

private fun CardModel.firmwareTile(): GettingStartedTileModel {
  return kind.shouldBeTypeOf<CardModel.Kind.GettingStarted>()
    .tiles
    .first { it.id == GettingStartedTileModel.Id.UpdateFirmware }
}

private fun CardModel.firmwareListItem() =
  content.shouldBeInstanceOf<DrillList>()
    .items
    .first { it.title == "Update firmware" }

private fun GettingStartedTask.listTitle(): String =
  when (id) {
    AddBitcoin -> "Add bitcoin"
    EnableSpendingLimit -> "Customize transfer settings"
  }
