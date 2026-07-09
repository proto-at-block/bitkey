package build.wallet.ui.app.transactions

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class TransactionDetailScreenPreviewSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("pending receive transaction detail") {
    paparazzi.snapshot {
      PendingReceiveTransactionDetailPreview()
    }
  }

  test("late send transaction detail") {
    paparazzi.snapshot {
      LateSendTransactionDetailPreview()
    }
  }

  test("sent transaction detail") {
    paparazzi.snapshot {
      SentTransactionDetailPreview()
    }
  }

  test("received transaction detail") {
    paparazzi.snapshot {
      ReceivedTransactionDetailPreview()
    }
  }

  test("utxo consolidation transaction detail") {
    paparazzi.snapshot {
      UtxoConsolidationTransactionDetailPreview()
    }
  }

  test("pending partnership transfer transaction detail") {
    paparazzi.snapshot {
      PendingPartnershipTransactionDetailPreview()
    }
  }

  test("pending partnership sale transaction detail") {
    paparazzi.snapshot {
      PendingPartnershipSaleTransactionDetailPreview()
    }
  }

  test("confirmed partnership sale transaction detail") {
    paparazzi.snapshot {
      ConfirmedPartnershipTransactionDetailPreview()
    }
  }

  test("confirmed partnership purchase transaction detail") {
    paparazzi.snapshot {
      ConfirmedPartnershipPurchaseTransactionDetailPreview()
    }
  }
})
