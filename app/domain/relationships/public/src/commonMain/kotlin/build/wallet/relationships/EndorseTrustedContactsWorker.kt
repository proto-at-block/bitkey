package build.wallet.relationships

import build.wallet.bitkey.relationships.TrustedContactKeyCertificate
import build.wallet.worker.AppWorker

/**
 * This worker periodically scans for new Recovery Contacts who have accepted invites
 * in their app. It identifies unauthenticated or unendorsed contacts and attempts
 * to endorse them accordingly.
 *
 * Refer to [TrustedContactKeyCertificate] for details.
 */
interface EndorseTrustedContactsWorker : AppWorker
