package build.wallet.inheritance

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake

val InheritanceTransactionDetailsFake = InheritanceTransactionDetails(
  claim = BeneficiaryLockedClaimFake,
  inheritanceWallet = SpendingWalletFake(),
  recipientAddress = BitcoinAddress("fake"),
  psbt = PsbtMock
)
