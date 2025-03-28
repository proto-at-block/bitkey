package bitkey.demo

import com.github.michaelbull.result.Result

interface DemoModeService {
  suspend fun enable(code: String): Result<Unit, Error>

  suspend fun disable(): Result<Unit, Error>
}
