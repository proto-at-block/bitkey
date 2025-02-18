package build.wallet.integration.statemachine.partnerships

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.testing.F8E_ENV_ENV_VAR_NAME

val CASH_APP = PartnerInfo(null, null, "Cash App", PartnerId("CashApp"))
val COINBASE = PartnerInfo(null, null, "Coinbase", PartnerId("Coinbase"))
val ROBINHOOD = PartnerInfo(null, null, "Robinhood", PartnerId("Robinhood"))
val MOONPAY = PartnerInfo(null, null, "MoonPay", PartnerId("MoonPay"))
val BLOCKCHAIN = PartnerInfo(null, null, "Blockchain", PartnerId("BlockchainCom"))
val TESTNET_FAUCET = PartnerInfo(null, null, "Testnet Faucet", PartnerId("TestnetFaucet"))
val SIGNET_FAUCET = PartnerInfo(null, null, "Signet Faucet", PartnerId("SignetFaucet"))

fun getEnvironment(): F8eEnvironment {
  return System.getenv(F8E_ENV_ENV_VAR_NAME)?.let {
    F8eEnvironment.parseString(it)
  } ?: F8eEnvironment.Local
}

fun getBitcoinNetworkType(f8eEnvironment: F8eEnvironment): BitcoinNetworkType {
  return when (f8eEnvironment) {
    F8eEnvironment.Production -> BitcoinNetworkType.BITCOIN
    F8eEnvironment.Staging -> BitcoinNetworkType.TESTNET
    else -> BitcoinNetworkType.REGTEST
  }
}
