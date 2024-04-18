package build.wallet.statemachine.home.full.card.gettingstarted

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_GETTINGSTARTED_COMPLETED
import build.wallet.analytics.v1.Action.ACTION_APP_WALLET_FUNDED
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.InternetUnreachable
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.bitkey.socrec.EndorsedTrustedContactFake1
import build.wallet.coroutines.turbine.turbines
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskId.InviteTrustedContact
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.limit.MobilePayBalanceMock
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconPhone
import build.wallet.statemachine.core.Icon.SmallIconPlusStroked
import build.wallet.statemachine.core.Icon.SmallIconShieldPerson
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayEnabledData
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

class GettingStartedCardUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val onAddBitcoinCalls = turbines.create<Unit>("add bitcoin calls")
  val onEnableSpendingLimitCalls = turbines.create<Unit>("enable spending limit calls")
  val onInviteTrustedContactCalls = turbines.create<Unit>("invite trusted contact calls")

  val gettingStartedTaskDao =
    GettingStartedTaskDaoMock(
      turbine = turbines::create
    )

  fun ActiveKeyboxLoadedMock(
    transactions: List<BitcoinTransaction>,
    mobilePayData: MobilePayData = LoadingMobilePayData,
  ): ActiveFullAccountLoadedData {
    return ActiveKeyboxLoadedDataMock.copy(
      transactionsData =
        ActiveKeyboxLoadedDataMock.transactionsData.copy(
          transactions = transactions.toImmutableList()
        ),
      mobilePayData = mobilePayData
    )
  }

  val props =
    GettingStartedCardUiProps(
      accountData = ActiveKeyboxLoadedMock(transactions = emptyList()),
      appFunctionalityStatus = AppFunctionalityStatus.FullFunctionality,
      trustedContacts = emptyList(),
      onAddBitcoin = { onAddBitcoinCalls.add(Unit) },
      onEnableSpendingLimit = { onEnableSpendingLimitCalls.add(Unit) },
      onInviteTrustedContact = { onInviteTrustedContactCalls += Unit },
      onShowAlert = {},
      onDismissAlert = {}
    )

  val stateMachine =
    GettingStartedCardUiStateMachineImpl(
      gettingStartedTaskDao = gettingStartedTaskDao,
      eventTracker = eventTracker
    )

  beforeTest {
    gettingStartedTaskDao.reset()
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
      cardModel.onClick("Turn on Mobile Pay").invoke()
      onEnableSpendingLimitCalls.awaitItem()
    }
  }

  test("onInviteTrustedContact click") {
    stateMachine.test(props) {
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

  test("complete all tasks") {
    stateMachine.test(props, useVirtualTime = true) {
      awaitItem().shouldBeNull()

      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(InviteTrustedContact, state = Incomplete),
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete)
        )
      )
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Incomplete),
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(InviteTrustedContact, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
            GettingStartedTask(AddBitcoin, state = Incomplete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
            GettingStartedTask(AddBitcoin, state = Complete),
            GettingStartedTask(EnableSpendingLimit, state = Incomplete)
          )
      )

      gettingStartedTaskDao.updateTask(EnableSpendingLimit, Complete)
      awaitItem().shouldNotBeNull().expect(
        tasks =
          listOf(
            GettingStartedTask(InviteTrustedContact, state = Complete),
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

      updateProps(
        props.copy(
          accountData =
            ActiveKeyboxLoadedMock(
              transactions = emptyList(),
              mobilePayData =
                MobilePayEnabledData(
                  activeSpendingLimit = SpendingLimitMock,
                  balance = MobilePayBalanceMock,
                  remainingFiatSpendingAmount = FiatMoney.usd(100),
                  changeSpendingLimit = { _, _, _, _ -> },
                  disableMobilePay = { },
                  refreshBalance = { }
                )
            )
        )
      )

      awaitItem()
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

      updateProps(
        props.copy(
          accountData = ActiveKeyboxLoadedMock(transactions = listOf(BitcoinTransactionFake))
        )
      )

      awaitItem()
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
    stateMachine.test(props) {
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

      updateProps(
        props.copy(
          trustedContacts = listOf(EndorsedTrustedContactFake1)
        )
      )

      awaitItem()
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
    stateMachine.test(
      props.copy(
        appFunctionalityStatus =
          AppFunctionalityStatus.LimitedFunctionality(
            cause =
              InternetUnreachable(
                Instant.DISTANT_PAST,
                Instant.DISTANT_PAST
              )
          )
      )
    ) {
      awaitItem().shouldBeNull()
      gettingStartedTaskDao.addTasks(
        listOf(
          GettingStartedTask(AddBitcoin, state = Incomplete),
          GettingStartedTask(EnableSpendingLimit, state = Incomplete),
          GettingStartedTask(InviteTrustedContact, state = Incomplete)
        )
      )

      val cardModel = awaitItem().shouldNotBeNull()
      cardModel.expectTaskModelWithEnabled(
        taskPairs =
          listOf(
            Pair(GettingStartedTask(AddBitcoin, state = Incomplete), false),
            Pair(GettingStartedTask(EnableSpendingLimit, state = Incomplete), false),
            Pair(GettingStartedTask(InviteTrustedContact, state = Incomplete), false)
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
          Complete -> Icon.SmallIconCheckFilled
          Incomplete ->
            when (task.id) {
              EnableSpendingLimit -> SmallIconPhone
              AddBitcoin -> SmallIconPlusStroked
              InviteTrustedContact -> SmallIconShieldPerson
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
