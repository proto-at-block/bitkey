package build.wallet.onboarding

import build.wallet.worker.AppWorker

/**
 * This worker is a fallback to ensure that users with full accounts call the complete-onboarding
 * endpoint; in rare cases where users restore from cloud backups created before onboarding is
 * complete, this call can be circumvented.
 * This worker will attempt to call complete-onboarding until it receives a successful response.
 */
interface OnboardingCompletionFailsafeWorker : AppWorker
