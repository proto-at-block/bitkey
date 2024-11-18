package build.wallet.notifications

import build.wallet.queueprocessor.Queue

interface RegisterWatchAddressQueue : Queue<RegisterWatchAddressContext>
