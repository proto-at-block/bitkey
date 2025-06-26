package build.wallet.inheritance

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimBothDescriptorsFake

val InheritanceTransactionDetailsFake = InheritanceTransactionDetails(
  claim = BeneficiaryLockedClaimBothDescriptorsFake,
  inheritanceWallet = SpendingWalletFake(),
  recipientAddress = BitcoinAddress("fake"),
  psbt = PsbtMock
)
