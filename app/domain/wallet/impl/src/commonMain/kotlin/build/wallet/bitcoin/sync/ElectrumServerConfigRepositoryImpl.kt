package build.wallet.bitcoin.sync

import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import io.ktor.http.*
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class ElectrumServerConfigRepositoryImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : ElectrumServerConfigRepository {
  override suspend fun storeF8eDefinedElectrumConfig(
    electrumServerDetails: ElectrumServerDetails,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        val existingConfig = electrumConfigQueries.loadElectrumConfig().executeAsOneOrNull()
        when (existingConfig) {
          null ->
            electrumConfigQueries.insertElectrumConfigEntity(
              f8eDefinedElectrumServerUrl = electrumServerDetails.url(),
              isCustomElectrumServerOn = false,
              customElectrumServerUrl = null
            )
          else ->
            electrumConfigQueries.updateF8eDefinedConfig(
              f8eDefinedElectrumServerUrl = electrumServerDetails.url()
            )
        }
      }
      .logFailure { "Failed to store F8e-defined Electrum config" }
  }

  override suspend fun storeUserPreference(
    preference: ElectrumServerPreferenceValue,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        val existingConfig = electrumConfigQueries.loadElectrumConfig().executeAsOneOrNull()
        val customElectrumServerDetails =
          when (preference) {
            is On -> preference.server.electrumServerDetails.url()
            is Off -> preference.previousUserDefinedElectrumServer?.electrumServerDetails?.url()
          }
        when (existingConfig) {
          null ->
            electrumConfigQueries.insertElectrumConfigEntity(
              f8eDefinedElectrumServerUrl = null,
              isCustomElectrumServerOn = preference is On,
              customElectrumServerDetails
            )
          else ->
            electrumConfigQueries.updateUserPreference(
              isCustomElectrumServerOn = preference is On,
              customElectrumServerDetails
            )
        }
      }
      .logFailure { "Failed to store user Electrum server preference" }
  }

  override fun getUserElectrumServerPreference(): Flow<ElectrumServerPreferenceValue?> =
    electrumServerEntity.map { entity ->
      when (entity?.isCustomElectrumServerOn) {
        false ->
          Off(
            previousUserDefinedElectrumServer =
              entity.customElectrumServerUrl?.let {
                val electrumUrl = Url(it)
                ElectrumServer.Custom(
                  electrumServerDetails =
                    ElectrumServerDetails(
                      host = electrumUrl.host,
                      port = electrumUrl.port.toString(10)
                    )
                )
              }
          )

        true ->
          entity.customElectrumServerUrl?.let {
            val electrumUrl = Url(it)
            On(
              server =
                ElectrumServer.Custom(
                  electrumServerDetails =
                    ElectrumServerDetails(
                      protocol = electrumUrl.protocol.name,
                      host = electrumUrl.host,
                      port = electrumUrl.port.toString(10)
                    )
                )
            )
          }

        null -> null
      }
    }

  override fun getF8eDefinedElectrumServer(): Flow<ElectrumServer?> =
    electrumServerEntity.map { entity ->
      entity?.f8eDefinedElectrumServerUrl?.let {
        val url = Url(it)
        ElectrumServer.F8eDefined(
          ElectrumServerDetails(
            protocol = url.protocol.name,
            host = url.host,
            port = url.port.toString(10)
          )
        )
      }
    }

  private val electrumServerEntity =
    flow {
      databaseProvider.database()
        .electrumConfigQueries
        .loadElectrumConfig()
        .asFlowOfOneOrNull()
        .transformLatest { queryResult ->
          queryResult
            .onSuccess { entity ->
              emit(entity)
            }
            .logFailure { "Error reading electrum server record from database" }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }
}
