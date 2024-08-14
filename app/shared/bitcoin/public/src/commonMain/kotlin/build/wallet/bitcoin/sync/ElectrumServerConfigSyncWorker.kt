package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.worker.AppWorker

/**
 * Syncs the F8e electrum configuration for the current [BitcoinNetworkType] and stores
 * it in the database.
 */
interface ElectrumServerConfigSyncWorker : AppWorker
