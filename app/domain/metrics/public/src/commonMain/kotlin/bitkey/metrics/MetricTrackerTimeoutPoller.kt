package bitkey.metrics

import build.wallet.worker.AppWorker

/**
 * Periodically checks if any metrics need to be ended due to timeout. This might occur due to
 * process death, crashes, or the user idling for too long.
 */
interface MetricTrackerTimeoutPoller : AppWorker
