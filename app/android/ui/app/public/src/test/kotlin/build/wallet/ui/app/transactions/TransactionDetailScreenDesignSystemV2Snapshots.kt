package build.wallet.ui.app.transactions

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class TransactionDetailScreenDesignSystemV2Snapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("pending receive transaction detail dsv2") {
    paparazzi.snapshot {
      PendingReceiveTransactionDetailDesignSystemV2Preview()
    }
  }

  test("late send transaction detail dsv2") {
    paparazzi.snapshot {
      LateSendTransactionDetailDesignSystemV2Preview()
    }
  }

  test("sent transaction detail dsv2") {
    paparazzi.snapshot {
      SentTransactionDetailDesignSystemV2Preview()
    }
  }

  test("received transaction detail dsv2") {
    paparazzi.snapshot {
      ReceivedTransactionDetailDesignSystemV2Preview()
    }
  }

  test("utxo consolidation transaction detail dsv2") {
    paparazzi.snapshot {
      UtxoConsolidationTransactionDetailDesignSystemV2Preview()
    }
  }

  test("pending partnership transfer transaction detail dsv2") {
    paparazzi.snapshot {
      PendingPartnershipTransactionDetailDesignSystemV2Preview()
    }
  }

  test("pending partnership sale transaction detail dsv2") {
    paparazzi.snapshot {
      PendingPartnershipSaleTransactionDetailDesignSystemV2Preview()
    }
  }

  test("confirmed partnership sale transaction detail dsv2") {
    paparazzi.snapshot {
      ConfirmedPartnershipTransactionDetailDesignSystemV2Preview()
    }
  }

  test("confirmed partnership purchase transaction detail dsv2") {
    paparazzi.snapshot {
      ConfirmedPartnershipPurchaseTransactionDetailDesignSystemV2Preview()
    }
  }
})
