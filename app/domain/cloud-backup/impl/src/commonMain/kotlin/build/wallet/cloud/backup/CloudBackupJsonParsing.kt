package build.wallet.cloud.backup

import bitkey.serialization.json.decodeFromStringResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.orElse
import kotlinx.serialization.json.Json

internal fun JsonSerializer.decodeCloudBackup(
  backupEncoded: String,
): Result<CloudBackup, Throwable> =
  decodeFromStringResult<CloudBackupV3>(backupEncoded)
    .orElse { decodeFromStringResult<CloudBackupV2>(backupEncoded) }

internal fun Json.decodeCloudBackup(
  backupEncoded: String,
): Result<CloudBackup, Throwable> =
  decodeFromStringResult<CloudBackupV3>(backupEncoded)
    .orElse { decodeFromStringResult<CloudBackupV2>(backupEncoded) }
