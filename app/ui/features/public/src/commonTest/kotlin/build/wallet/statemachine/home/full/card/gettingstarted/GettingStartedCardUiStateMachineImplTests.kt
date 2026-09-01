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
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedSectionModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedTileModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconTint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant

class GettingStartedCardUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val onAddBitcoinCalls = turbines.create<Unit>("add bitcoin calls")
  val onEnableSpendingLimitCalls = turbines.create<Unit>("enable spending limit calls")
  val onUpdateFirmwareCalls = turbines.create<Unit>("update firmware calls")

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
      awaitItem().shouldNotBeNull().asGettingStarted().expect(
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
      awaitItem().shouldNotBeNull().asGettingStarted().expect(
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

      val cardModel = awaitItem().shouldNotBeNull().asGettingStarted()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )
      cardModel.tileOnClick("Add bitcoin").invoke()
      onAddBitcoinCalls.awaitItem()
    }
  }

  test("onEnableSpendingLimit click") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(EnableSpendingLimit, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull().asGettingStarted()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(EnableSpendingLimit, state = Incomplete))
      )
      cardModel.tileOnClick("Customize transfer settings").invoke()
      onEnableSpendingLimitCalls.awaitItem()
    }
  }

  test("shows firmware update tile first when available") {
    stateMachine.test(props.copy(showUpdateFirmwareTile = true)) {
      val firmwareOnlyCardModel = awaitItem().shouldNotBeNull().asGettingStarted()
      val firmwareTile = firmwareOnlyCardModel.tiles
        .first { it.id == GettingStartedTileModel.Id.UpdateFirmware }
      firmwareTile.title.shouldBe("Update firmware")
      firmwareTile.isEnabled.shouldBe(true)
      firmwareTile.isComplete.shouldBe(false)
      firmwareTile.leadingIcon.shouldNotBeNull()
        .iconImage.shouldBeTypeOf<IconImage.LocalImage>()
        .icon.shouldBe(DotBitkey)

      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )

      val cardModel = awaitItem().shouldNotBeNull().asGettingStarted()
      cardModel.expect(
        tasks = listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )

      // Firmware tile is still present
      cardModel.tiles.first().id.shouldBe(GettingStartedTileModel.Id.UpdateFirmware)
    }
  }

  test("onUpdateFirmware click") {
    stateMachine.test(props.copy(showUpdateFirmwareTile = true)) {
      val cardModel = awaitItem().shouldNotBeNull().asGettingStarted()
      cardModel.firmwareTile().onClick.shouldNotBeNull().invoke()
      onUpdateFirmwareCalls.awaitItem()
    }
  }

  test("keeps firmware update card after onboarding tasks clear") {
    stateMachine.test(props.copy(showUpdateFirmwareTile = true)) {
      awaitItem().shouldNotBeNull().asGettingStarted().firmwareTile()

      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(AddBitcoin, state = Incomplete))
      )
      awaitItem().shouldNotBeNull()

      gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
      awaitItem().shouldNotBeNull()

      gettingStartedTaskDao.clearTasksCalls.awaitItem()

      val firmwareOnlyCard = awaitItem().shouldNotBeNull().asGettingStarted()
      firmwareOnlyCard.tiles.single().id
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
      awaitItem().shouldNotBeNull().asGettingStarted().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
      awaitItem().shouldNotBeNull().asGettingStarted().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(EnableSpendingLimit, Complete)
      awaitItem().shouldNotBeNull().asGettingStarted().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Complete)
          )
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

      awaitItem().shouldNotBeNull().asGettingStarted().expect(
        tasks =
          listOf(
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      mobilePayService.mobilePayData.value = MobilePayEnabledDataMock

      awaitItem().shouldNotBeNull().asGettingStarted().expect(
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

      awaitItem().shouldNotBeNull().asGettingStarted().expect(
        tasks =
          listOf(
            GettingStartedTask(EnableSpendingLimit, state = Incomplete),
            GettingStartedTask(AddBitcoin, state = Incomplete)
          )
      )

      bitcoinWalletService.transactionsData.value = TransactionsDataMock

      awaitItem().shouldNotBeNull().asGettingStarted().expect(
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

      val cardModel = awaitItem().shouldNotBeNull().asGettingStarted()
      cardModel.expectTilesWithEnabled(
        taskPairs =
          listOf(
            Pair(GettingStartedTask(AddBitcoin, state = Incomplete), false),
            Pair(GettingStartedTask(EnableSpendingLimit, state = Incomplete), false)
          )
      )
    }
  }
})

private fun GettingStartedSectionModel.asGettingStarted(): GettingStartedSectionModel = this

private fun GettingStartedSectionModel.expect(tasks: List<GettingStartedTask>) =
  expectTilesWithEnabled(taskPairs = tasks.map { Pair(it, true) })

private fun GettingStartedSectionModel.expectTilesWithEnabled(
  taskPairs: List<Pair<GettingStartedTask, Boolean>>,
) {
  title.shouldBe("Getting Started")
  for ((task, taskEnabled) in taskPairs) {
    val tile = tiles.first {
      it.id == when (task.id) {
        AddBitcoin -> GettingStartedTileModel.Id.AddBitcoin
        EnableSpendingLimit -> GettingStartedTileModel.Id.EnableSpendingLimit
      }
    }
    tile.title.shouldBe(task.listTitle())
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
    tile.leadingIcon.iconTint.shouldBe(
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

private fun GettingStartedSectionModel.tileOnClick(taskTitle: String): (() -> Unit) {
  return tiles.first { it.title == taskTitle }.onClick.shouldNotBeNull()
}

private fun GettingStartedSectionModel.firmwareTile(): GettingStartedTileModel =
  tiles.first { it.id == GettingStartedTileModel.Id.UpdateFirmware }

private fun GettingStartedTask.listTitle(): String =
  when (id) {
    AddBitcoin -> "Add bitcoin"
    EnableSpendingLimit -> "Customize transfer settings"
  }
