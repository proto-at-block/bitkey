package build.wallet.platform.data

class FileManagerMock : FileManager {
  var files = mutableMapOf<String, ByteArray>()
  var failWrite: Boolean = false

  override suspend fun writeFile(
    data: ByteArray,
    fileName: String,
  ): FileManagerResult<Unit> {
    if (failWrite) {
      return FileManagerResult.Err(FileManagerError(Throwable("write failed")))
    } else {
      files[fileName] = data
      return FileManagerResult.Ok(Unit)
    }
  }

  override suspend fun deleteFile(fileName: String): FileManagerResult<Unit> {
    files.remove(fileName)
    return FileManagerResult.Ok(Unit)
  }

  override suspend fun readFileAsBytes(fileName: String): FileManagerResult<ByteArray> {
    return if (!files.containsKey(fileName)) {
      FileManagerResult.Err(FileManagerError(Throwable("file not found")))
    } else {
      FileManagerResult.Ok(files[fileName]!!)
    }
  }

  override suspend fun readFileAsString(fileName: String): FileManagerResult<String> {
    return if (!files.containsKey(fileName)) {
      FileManagerResult.Err(FileManagerError(Throwable("file not found")))
    } else {
      FileManagerResult.Ok(files[fileName]!!.decodeToString())
    }
  }

  override suspend fun unzipFile(
    zipPath: String,
    targetDirectory: String,
  ): FileManagerResult<Unit> {
    return FileManagerResult.Ok(Unit)
  }

  override suspend fun fileExists(fileName: String): Boolean {
    return files.contains(fileName)
  }

  override suspend fun removeDir(path: String): FileManagerResult<Unit> {
    return FileManagerResult.Ok(Unit)
  }

  override suspend fun mkdirs(path: String): FileManagerResult<Boolean> {
    return FileManagerResult.Ok(true)
  }
}
