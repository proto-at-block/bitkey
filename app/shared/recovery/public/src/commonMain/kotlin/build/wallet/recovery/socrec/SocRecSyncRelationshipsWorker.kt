package build.wallet.recovery.socrec

import build.wallet.worker.AppWorker

/**
 * Worker that periodically syncs SocRec relationships.
 * The relationships are accessible through [SocRecService.relationships].
 */
interface SocRecSyncRelationshipsWorker : AppWorker
