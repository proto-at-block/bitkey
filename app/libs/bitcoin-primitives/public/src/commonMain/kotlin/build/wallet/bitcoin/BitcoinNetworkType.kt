package build.wallet.bitcoin

enum class BitcoinNetworkType {
  BITCOIN,
  SIGNET,
  TESTNET,
  REGTEST,
  ;

  // For extensions
  companion object
}
