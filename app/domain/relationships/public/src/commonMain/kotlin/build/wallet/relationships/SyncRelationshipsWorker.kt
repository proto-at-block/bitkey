package build.wallet.relationships

import build.wallet.worker.AppWorker

/**
 * Worker that periodically syncs trusted contact  relationships.
 * The relationships are accessible through [RelationshipsService.relationships].
 */
interface SyncRelationshipsWorker : AppWorker
