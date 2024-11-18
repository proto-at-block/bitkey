package build.wallet.analytics.events

import build.wallet.queueprocessor.Processor

interface AnalyticsEventProcessor : Processor<QueueAnalyticsEvent>
