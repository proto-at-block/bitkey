package build.wallet.f8e

import build.wallet.keybox.KeyboxDao
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActiveF8eEnvironmentRepositoryImpl(
  private val keyboxDao: KeyboxDao,
) : ActiveF8eEnvironmentRepository {
  override fun activeF8eEnvironment(): Flow<Result<F8eEnvironment?, Error>> {
    return keyboxDao.activeKeybox().map { result ->
      result.map { it?.config?.f8eEnvironment }
    }
  }
}
