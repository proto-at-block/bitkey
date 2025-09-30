package build.wallet.home

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly

class GettingStartedTaskDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  val task1 = GettingStartedTask(id = AddBitcoin, state = Incomplete)
  val task2 = GettingStartedTask(id = EnableSpendingLimit, state = Incomplete)

  lateinit var dao: GettingStartedTaskDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = GettingStartedTaskDaoImpl(databaseProvider)
  }

  test("Add getting started tasks") {
    dao.getTasks().shouldBeEmpty()
    dao.tasks().test {
      awaitItem().shouldBeEmpty()
      dao.addTasks(listOf(task1, task2))
      dao.getTasks().shouldContainExactly(task1, task2)
      awaitItem().shouldContainExactly(task1, task2)
    }
  }

  test("Update getting started task") {
    dao.getTasks().shouldBeEmpty()
    dao.tasks().test {
      awaitItem().shouldBeEmpty()
      dao.addTasks(listOf(task1, task2))
      awaitItem().shouldContainExactly(task1, task2)

      dao.updateTask(id = task2.id, state = Complete)
      awaitItem().shouldContainExactly(task1, task2.copy(state = Complete))
    }
  }

  test("Remove getting started tasks") {
    dao.getTasks().shouldBeEmpty()
    dao.tasks().test {
      awaitItem().shouldBeEmpty()
      dao.addTasks(listOf(task1, task2))
      awaitItem().shouldContainExactly(task1, task2)

      dao.clearTasks()
      awaitItem().shouldBeEmpty()
      dao.getTasks().shouldBeEmpty()
    }
  }
})
