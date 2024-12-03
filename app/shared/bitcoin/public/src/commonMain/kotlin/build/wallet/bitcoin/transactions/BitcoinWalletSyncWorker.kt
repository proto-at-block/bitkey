package build.wallet.bitcoin.transactions

import build.wallet.worker.AppWorker

/**
 * Worker that periodically syncs active account's wallet balance, utxos and on-chain transactions.
 * The wallet can be also manually synced on demand through [BitcoinWalletService.sync].
 *
 * Latest balance and transactions are emitted into [BitcoinWalletService.spendingWallet].
 */
interface BitcoinWalletSyncWorker : AppWorker
