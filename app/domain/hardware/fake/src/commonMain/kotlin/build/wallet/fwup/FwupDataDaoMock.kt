package build.wallet.fwup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.firmware.McuRole
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FwupDataDaoMock(
  private val turbine: (name: String) -> Turbine<Any>,
) : FwupDataDao {
  var clearCalls = turbine("clear fwup data dao calls")
  var setMcuSequenceIdCalls = turbine("set mcu sequence id calls")
  var clearAllMcuStatesCalls = turbine("clear all mcu states dao calls")
  var setMcuFwupDataCalls = turbine("set mcu fwup data calls")
  var clearAllMcuFwupDataCalls = turbine("clear all mcu fwup data calls")
  var clearMcuFwupDataCalls = turbine("clear mcu fwup data calls")

  val mcuFwupDataFlow = MutableStateFlow<Result<List<McuFwupData>, Error>>(Ok(emptyList()))

  private val mcuSequenceIds = mutableMapOf<McuRole, UInt>()
  private val mcuFwupDataMap = mutableMapOf<McuRole, McuFwupData>()

  override suspend fun setMcuFwupData(mcuFwupDataList: List<McuFwupData>): Result<Unit, Error> {
    setMcuFwupDataCalls += mcuFwupDataList
    mcuFwupDataList.forEach { data ->
      mcuFwupDataMap[data.mcuRole] = data
    }
    mcuFwupDataFlow.value = Ok(mcuFwupDataMap.values.toList())
    return Ok(Unit)
  }

  override suspend fun getMcuFwupData(mcuRole: McuRole): Result<McuFwupData?, Error> {
    return Ok(mcuFwupDataMap[mcuRole])
  }

  override suspend fun getAllMcuFwupData(): Result<List<McuFwupData>, Error> {
    return Ok(mcuFwupDataMap.values.toList())
  }

  override suspend fun clearAllMcuFwupData(): Result<Unit, Error> {
    clearAllMcuFwupDataCalls += Unit
    mcuFwupDataMap.clear()
    mcuFwupDataFlow.value = Ok(emptyList())
    return Ok(Unit)
  }

  override suspend fun clearMcuFwupData(mcuRole: McuRole): Result<Unit, Error> {
    clearMcuFwupDataCalls += mcuRole
    mcuFwupDataMap.remove(mcuRole)
    mcuFwupDataFlow.value = Ok(mcuFwupDataMap.values.toList())
    return Ok(Unit)
  }

  override fun mcuFwupData(): Flow<Result<List<McuFwupData>, Error>> = mcuFwupDataFlow

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls += Unit
    return Ok(Unit)
  }

  override suspend fun getMcuSequenceId(mcuRole: McuRole): Result<UInt, Error> {
    return Ok(
      mcuSequenceIds[mcuRole]
        ?: throw NoSuchElementException("No MCU sequence ID found for $mcuRole in the database.")
    )
  }

  override suspend fun setMcuSequenceId(
    mcuRole: McuRole,
    sequenceId: UInt,
  ): Result<Unit, Error> {
    setMcuSequenceIdCalls += (mcuRole to sequenceId)
    mcuSequenceIds[mcuRole] = sequenceId
    return Ok(Unit)
  }

  override suspend fun clearAllMcuStates(): Result<Unit, Error> {
    mcuSequenceIds.clear()
    clearAllMcuStatesCalls += Unit
    return Ok(Unit)
  }

  fun reset(testName: String) {
    mcuFwupDataFlow.value = Ok(emptyList())
    mcuSequenceIds.clear()
    mcuFwupDataMap.clear()
    clearCalls = turbine("clear fwup data dao calls for $testName")
    setMcuSequenceIdCalls = turbine("set mcu sequence id calls for $testName")
    clearAllMcuStatesCalls = turbine("clear all mcu states dao calls for $testName")
    setMcuFwupDataCalls = turbine("set mcu fwup data calls for $testName")
    clearAllMcuFwupDataCalls = turbine("clear all mcu fwup data calls for $testName")
    clearMcuFwupDataCalls = turbine("clear mcu fwup data calls for $testName")
  }
}
