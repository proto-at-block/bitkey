package build.wallet.analytics.events

import build.wallet.queueprocessor.Queue

interface AnalyticsEventQueue : Queue<QueueAnalyticsEvent>
