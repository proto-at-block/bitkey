package build.wallet.inheritance

import build.wallet.worker.AppWorker

/**
 * Triggers an inheritance material sync whenever keys or contacts change.
 */
interface InheritanceMaterialSyncWorker : AppWorker
