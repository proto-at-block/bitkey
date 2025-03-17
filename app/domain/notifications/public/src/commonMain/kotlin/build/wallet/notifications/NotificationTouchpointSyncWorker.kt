package build.wallet.notifications

import build.wallet.worker.AppWorker

/**
 * Retrieves the latest notification touchpoints from F8e and stores them locally. Any changes
 * will be emitted in [NotificationTouchpointService.notificationTouchpointData].
 */
interface NotificationTouchpointSyncWorker : AppWorker
