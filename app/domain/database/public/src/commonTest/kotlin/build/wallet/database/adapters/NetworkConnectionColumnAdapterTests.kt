package build.wallet.database.adapters

import build.wallet.availability.NetworkConnection
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Custom
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NetworkConnectionColumnAdapterTests : FunSpec({
  test("decode from string failure") {
    NetworkConnectionColumnAdapter
      .decode("some-value-not-decodable")
      .shouldBe(NetworkConnection.UnknownNetworkConnection)
  }

  context("Bitstamp Network Connection") {
    val connection = NetworkConnection.HttpClientNetworkConnection.Bitstamp
    val dbValue =
      """
      {"type":"build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.Bitstamp"}
      """.trimIndent()

    test("decode from string") {
      NetworkConnectionColumnAdapter.decode(dbValue).shouldBe(connection)
    }

    test("encode as string") {
      NetworkConnectionColumnAdapter.encode(connection).shouldBe(dbValue)
    }
  }

  context("Memfault Network Connection") {
    val connection = NetworkConnection.HttpClientNetworkConnection.Memfault
    val dbValue =
      """
      {"type":"build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.Memfault"}
      """.trimIndent()

    test("decode from string") {
      NetworkConnectionColumnAdapter.decode(dbValue).shouldBe(connection)
    }

    test("encode as string") {
      NetworkConnectionColumnAdapter.encode(connection).shouldBe(dbValue)
    }
  }

  context("Mempool Network Connection") {
    val connection = NetworkConnection.HttpClientNetworkConnection.Mempool
    val dbValue =
      """
      {"type":"build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.Mempool"}
      """.trimIndent()

    test("decode from string") {
      NetworkConnectionColumnAdapter.decode(dbValue).shouldBe(connection)
    }

    test("encode as string") {
      NetworkConnectionColumnAdapter.encode(connection).shouldBe(dbValue)
    }
  }

  context("Augur Fees Network Connection") {
    val connection = NetworkConnection.HttpClientNetworkConnection.AugurFees
    val dbValue =
      """
      {"type":"build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.AugurFees"}
      """.trimIndent()

    test("decode from string") {
      NetworkConnectionColumnAdapter.decode(dbValue).shouldBe(connection)
    }

    test("encode as string") {
      NetworkConnectionColumnAdapter.encode(connection).shouldBe(dbValue)
    }
  }

  context("F8e Network Connection") {
    fun connection(environment: F8eEnvironment) =
      NetworkConnection.HttpClientNetworkConnection.F8e(environment)

    fun dbValue(environment: F8eEnvironment) =
      """
      {"type":"build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e","environment":"${environment.asString()}"}
      """.trimIndent()

    test("decode from string") {
      listOf(Production, Staging, Development, Local, Custom("abc.com"))
        .forEach {
          NetworkConnectionColumnAdapter.decode(dbValue(it)).shouldBe(connection(it))
        }
    }

    test("encode as string") {
      listOf(Production, Staging, Development, Local, Custom("abc.com"))
        .forEach {
          NetworkConnectionColumnAdapter.encode(connection(it)).shouldBe(dbValue(it))
        }
    }
  }

  context("Electrum Syncer Network Connection") {
    val connection = NetworkConnection.ElectrumSyncerNetworkConnection
    val dbValue =
      """
      {"type":"build.wallet.availability.NetworkConnection.ElectrumSyncerNetworkConnection"}
      """.trimIndent()

    test("decode from string") {
      NetworkConnectionColumnAdapter.decode(dbValue).shouldBe(connection)
    }

    test("encode as string") {
      NetworkConnectionColumnAdapter.encode(connection).shouldBe(dbValue)
    }
  }
})
