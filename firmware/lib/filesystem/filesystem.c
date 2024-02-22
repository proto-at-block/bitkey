#include "filesystem.h"

#include "assert.h"
#include "attributes.h"
#include "lfs_bd.h"
#include "log.h"
#include "perf.h"
#include "rtos_event_groups.h"
#include "rtos_mutex.h"
#include "rtos_semaphore.h"
#include "rtos_thread.h"
#include "sysevent.h"

/* RTOS lock/unlock syntactic sugar */
#define LOCK_UNLOCK(A)            \
  ({                              \
    perf_count(perf.lock_unlock); \
    int retval = -1;              \
    if (lock()) {                 \
      retval = A;                 \
      unlock();                   \
    }                             \
    if (retval < 0) {             \
      perf_count(perf.errors);    \
    }                             \
    retval;                       \
  })
static bool lock(void);
static bool unlock(void);

#define FS_ERR_STR_MAX_LEN          64
#define BIT_FS_MOUNT                (1 << 0)
#define GLOBAL_FILE_LOCK_TIMEOUT_MS (1000 * 10)

static lfs_t* lfs;
static rtos_mutex_t fs_lock;
static rtos_semaphore_t g_file_access_lock;
static rtos_semaphore_t mount_lock;
static rtos_thread_t* mount_fs_thread = NULL;
static bool is_fs_ready = false;
static bool is_mounted = false;

// Since fs_file_t is about ~8K in size, we maintain one global file with a mutex so that
// tasks do not have to allocate a large amount of memory in their task stacks for filesystem
// operations.
static fs_file_t g_file;

static struct {
  perf_counter_t* mount;
  perf_counter_t* open;
  perf_counter_t* close;
  perf_counter_t* lock_unlock;
  perf_counter_t* errors;
} perf;

static void fs_mount_task(void* UNUSED(arg));

void fs_mount(void) {
  perf.mount = perf_create(PERF_ELAPSED, fs_mount);
  perf.open = perf_create(PERF_COUNT, fs_open);
  perf.close = perf_create(PERF_COUNT, fs_close);
  perf.lock_unlock = perf_create(PERF_COUNT, fs_lock_unlock);
  perf.errors = perf_create(PERF_COUNT, fs_errors);

  rtos_mutex_create(&fs_lock);
  rtos_semaphore_create(&mount_lock);
  rtos_semaphore_create(&g_file_access_lock);

  mount_fs_thread = rtos_thread_create(fs_mount_task, NULL, RTOS_THREAD_PRIORITY_LOW, 2048);
  ASSERT(mount_fs_thread != NULL);
}

static void fs_mount_task(void* UNUSED(arg)) {
  perf_begin(perf.mount);

  rtos_semaphore_give(&mount_lock);
  rtos_semaphore_give(&g_file_access_lock);

  /* Lock the FS until it's unlocked by fs_mount */
  rtos_semaphore_take(&mount_lock, RTOS_SEMAPHORE_TIMEOUT_MAX);

  /* Mount the filesystem */
  lfs = bd_mount();

  if (lfs != NULL) {
    rtos_semaphore_give(&mount_lock);
    is_mounted = true;
  } else {
    LOGE("Failed to mount filesystem");
    goto cleanup;
  }

  is_fs_ready = true;
  sysevent_set(SYSEVENT_FILESYSTEM_READY);

#if 0
  /* Boot count file for testing filesystem */
  // read current count
  static fs_file_t file;
  uint32_t boot_count = 0;
  fs_open(&file, "boot_count", FS_O_RDWR | FS_O_CREAT);
  fs_file_read(&file, &boot_count, sizeof(boot_count));

  // update boot count
  boot_count += 1;
  fs_file_rewind(&file);
  fs_file_write(&file, &boot_count, sizeof(boot_count));

  // remember the storage is not updated until the file is closed successfully
  fs_close(&file);

  // print the boot count
  LOGD("boot_count: %" PRId32, boot_count);
#endif

cleanup:
  perf_end(perf.mount);
  rtos_thread_delete(mount_fs_thread);
}

int32_t fs_used(void) {
  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK((int32_t)lfs_fs_size(lfs));
}

char* fs_error_str(const int error) {
  static char err_buf[FS_ERR_STR_MAX_LEN];
  memset(&err_buf, 0, FS_ERR_STR_MAX_LEN);

  switch (error) {
    case LFS_ERR_OK:
      break;
    case LFS_ERR_IO:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Error during device operation");
      break;
    case LFS_ERR_CORRUPT:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Corrupted");
      break;
    case LFS_ERR_NOENT:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "No file or directory entry");
      break;
    case LFS_ERR_EXIST:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Entry already exists");
      break;
    case LFS_ERR_NOTDIR:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Entry is not a dir");
      break;
    case LFS_ERR_ISDIR:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Entry is a dir");
      break;
    case LFS_ERR_NOTEMPTY:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Dir is not empty");
      break;
    case LFS_ERR_BADF:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Bad file number");
      break;
    case LFS_ERR_FBIG:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "File too large");
      break;
    case LFS_ERR_INVAL:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Invalid parameter");
      break;
    case LFS_ERR_NOSPC:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "No space left on device");
      break;
    case LFS_ERR_NOMEM:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "No more memory available");
      break;
    case LFS_ERR_NOATTR:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "No data/attr available");
      break;
    case LFS_ERR_NAMETOOLONG:
      snprintf(err_buf, FS_ERR_STR_MAX_LEN, "File name too long");
      break;
    default:
      if (!bd_error_str(err_buf, FS_ERR_STR_MAX_LEN, error)) {
        snprintf(err_buf, FS_ERR_STR_MAX_LEN, "Unknown error (%i)", error);
      }
      break;
  }

  return err_buf;
}

int fs_open(fs_file_t* file, const char* path, int flags) {
  ASSERT(file != NULL);
  ASSERT(path != NULL);

  if (lfs == NULL || file == NULL) {
    return -1;
  }

  perf_count(perf.open);

  memset(file->handle, 0, sizeof(file->handle));
  memset(file->file_buffer, 0, sizeof(file->file_buffer));

  static struct lfs_file_config defaults = {0};
  defaults.buffer = file->file_buffer;

  int ret;
  if (rtos_in_isr()) {
    ret = lfs_file_opencfg(lfs, (lfs_file_t*)file->handle, path, flags, &defaults);
  } else {
    ret = LOCK_UNLOCK(lfs_file_opencfg(lfs, (lfs_file_t*)file->handle, path, flags, &defaults));
  }

  return ret;
}

int fs_close(fs_file_t* file) {
  ASSERT(file != NULL);

  if (lfs == NULL || file == NULL) {
    return -1;
  }

  perf_count(perf.close);

  if (rtos_in_isr()) {
    return lfs_file_close(lfs, (lfs_file_t*)file->handle);
  } else {
    return LOCK_UNLOCK(lfs_file_close(lfs, (lfs_file_t*)file->handle));
  }
}

int fs_open_global(fs_file_t** file, const char* path, int flags) {
  ASSERT(file != NULL);
  *file = &g_file;

  if (rtos_in_isr()) {
    if (!rtos_semaphore_take_from_isr(&g_file_access_lock)) {
      return -1;
    }
  } else {
    if (!rtos_semaphore_take(&g_file_access_lock, GLOBAL_FILE_LOCK_TIMEOUT_MS)) {
      return -1;
    }
  }

  int ret = fs_open(*file, path, flags);
  if (ret != 0) {
    if (rtos_in_isr()) {
      rtos_semaphore_give_from_isr(&g_file_access_lock);
    } else {
      rtos_semaphore_give(&g_file_access_lock);
    }
  }

  return ret;
}

int fs_close_global(fs_file_t* file) {
  ASSERT(file == &g_file);
  int ret = fs_close(file);

  if (rtos_in_isr()) {
    rtos_semaphore_give_from_isr(&g_file_access_lock);
  } else {
    rtos_semaphore_give(&g_file_access_lock);
  }
  return ret;
}

int fs_remove(const char* path) {
  ASSERT(path != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_remove(lfs, path));
}

int fs_rename(const char* oldpath, const char* newpath) {
  ASSERT(oldpath != NULL);
  ASSERT(newpath != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_rename(lfs, oldpath, newpath));
}

bool fs_file_exists(const char* path) {
  ASSERT(path != NULL);

  const fs_filetype_t ret = fs_get_filetype(path);

  // Does file exist?
  if (ret == FS_FILE_TYPE_REG) {
    return true;
  }

  return false;
}

int fs_get_filesize(const char* path) {
  ASSERT(path != NULL);

  if (lfs == NULL) {
    return -1;
  }

  int ret = -1;
  struct lfs_info info;

  ret = LOCK_UNLOCK(lfs_stat(lfs, path, &info));

  if (ret < 0) {
    return ret;
  }

  if (info.type == LFS_TYPE_REG) {
    return (int32_t)info.size;
  }

  return -1;
}

fs_filetype_t fs_get_filetype(const char* path) {
  ASSERT(path != NULL);

  struct lfs_info info;

  if (lfs == NULL) {
    return -1;
  }

  int ret = LOCK_UNLOCK(lfs_stat(lfs, path, &info));

  if (ret < 0) {
    return FS_FILE_TYPE_ERR;
  }

  if (info.type == LFS_TYPE_REG) {
    return FS_FILE_TYPE_REG;
  } else if (info.type == LFS_TYPE_DIR) {
    return FS_FILE_TYPE_DIR;
  }

  return FS_FILE_TYPE_ERR;
}

int fs_touch(const char* path) {
  ASSERT(path != NULL);

  if (lfs == NULL) {
    return -1;
  }

  static fs_file_t touch_file;
  memset(touch_file.handle, 0, sizeof(touch_file.handle));
  memset(touch_file.file_buffer, 0, sizeof(touch_file.file_buffer));

  static struct lfs_file_config defaults = {0};
  defaults.buffer = touch_file.file_buffer;

  int ret = LOCK_UNLOCK(
    lfs_file_opencfg(lfs, (lfs_file_t*)touch_file.handle, path, LFS_O_CREAT, &defaults));

  if (ret < 0) {
    return ret;
  }

  ret = LOCK_UNLOCK(lfs_file_close(lfs, (lfs_file_t*)touch_file.handle));

  return ret;
}

int fs_file_sync(fs_file_t* file) {
  ASSERT(file != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_file_sync(lfs, (lfs_file_t*)file->handle));
}

int32_t fs_file_read(fs_file_t* file, void* buffer, uint32_t size) {
  ASSERT(file != NULL);
  ASSERT(buffer != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_file_read(lfs, (lfs_file_t*)file->handle, buffer, (lfs_size_t)size));
}

int32_t fs_file_write(fs_file_t* file, const void* buffer, uint32_t size) {
  ASSERT(file != NULL);
  ASSERT(buffer != NULL);

  if (lfs == NULL) {
    return -1;
  }

  if (rtos_in_isr()) {
    return (int32_t)lfs_file_write(lfs, (lfs_file_t*)file->handle, buffer, (lfs_size_t)size);
  } else {
    return LOCK_UNLOCK(
      (int32_t)lfs_file_write(lfs, (lfs_file_t*)file->handle, buffer, (lfs_size_t)size));
  }
}

int32_t fs_file_seek(fs_file_t* file, int32_t off, fs_whence_flags_t whence) {
  ASSERT(file != NULL);

  if (lfs == NULL || file == NULL) {
    return -1;
  }

  if (rtos_in_isr()) {
    return (int32_t)lfs_file_seek(lfs, (lfs_file_t*)file->handle, off, (int)whence);
  } else {
    return LOCK_UNLOCK((int32_t)lfs_file_seek(lfs, (lfs_file_t*)file->handle, off, (int)whence));
  }
}

int fs_file_truncate(fs_file_t* file, int32_t size) {
  ASSERT(file != NULL);

  if (lfs == NULL || file == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_file_truncate(lfs, (lfs_file_t*)file->handle, (lfs_off_t)size));
}

int32_t fs_file_tell(fs_file_t* file) {
  ASSERT(file != NULL);

  if (lfs == NULL || file == NULL) {
    return -1;
  }

  return LOCK_UNLOCK((int32_t)lfs_file_tell(lfs, (lfs_file_t*)file->handle));
}

int fs_file_rewind(fs_file_t* file) {
  ASSERT(file != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_file_rewind(lfs, (lfs_file_t*)file->handle));
}

int32_t fs_file_size(fs_file_t* file) {
  ASSERT(file != NULL);

  if (lfs == NULL) {
    return -1;
  }

  if (rtos_in_isr()) {
    return lfs_file_size(lfs, (lfs_file_t*)file->handle);
  } else {
    return LOCK_UNLOCK(lfs_file_size(lfs, (lfs_file_t*)file->handle));
  }
}

int fs_mkdir(const char* path) {
  ASSERT(path != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_mkdir(lfs, path));
}

int fs_ensure_file_path(const char* path) {
  ASSERT(path != NULL);

  if (lfs == NULL) {
    return -1;
  }

  int ret;
  const uint32_t len = strlen(path);
  char temp_path[FS_FILE_NAME_MAX_LEN];
  for (uint32_t inx = 0; inx < len; inx++) {
    if (path[inx] == '/') {
      memcpy(temp_path, path, inx);
      temp_path[inx] = 0;
      ret = LOCK_UNLOCK(lfs_mkdir(lfs, temp_path));
      if (ret < 0) {
        if (ret != LFS_ERR_EXIST) {
          return ret;
        }
      } else {
        LOGI("Created directory: %s", temp_path);
      }
    }
  }
  return 0;
}

int fs_dir_open(fs_dir_t* dir, const char* path) {
  ASSERT(dir != NULL);
  ASSERT(path != NULL);

  if (lfs == NULL) {
    return -1;
  }

  memset(dir->handle, 0, sizeof(dir->handle));

  return LOCK_UNLOCK(lfs_dir_open(lfs, (lfs_dir_t*)dir->handle, path));
}

int fs_dir_close(fs_dir_t* dir) {
  ASSERT(dir != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_dir_close(lfs, (lfs_dir_t*)dir->handle));
}

int fs_dir_read(fs_dir_t* dir, fs_dir_info_t* info) {
  ASSERT(dir != NULL);
  ASSERT(info != NULL);

  if (lfs == NULL) {
    return -1;
  }

  struct lfs_info lfs_info;
  int ret = LOCK_UNLOCK(lfs_dir_read(lfs, (lfs_dir_t*)dir->handle, &lfs_info));

  if (ret >= 0) {
    if (lfs_info.type == LFS_TYPE_REG) {
      info->type = FS_FILE_TYPE_REG;
    } else if (lfs_info.type == LFS_TYPE_DIR) {
      info->type = FS_FILE_TYPE_DIR;
    } else {
      info->type = FS_FILE_TYPE_ERR;
    }

    info->size = lfs_info.size;
    strcpy(info->name, lfs_info.name);
  }

  return ret;
}

int fs_dir_seek(fs_dir_t* dir, int32_t off) {
  ASSERT(dir != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_dir_seek(lfs, (lfs_dir_t*)dir->handle, (lfs_off_t)off));
}

int32_t fs_dir_tell(fs_dir_t* dir) {
  ASSERT(dir != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_dir_tell(lfs, (lfs_dir_t*)dir->handle));
}

int fs_dir_rewind(fs_dir_t* dir) {
  ASSERT(dir != NULL);

  if (lfs == NULL) {
    return -1;
  }

  return LOCK_UNLOCK(lfs_dir_rewind(lfs, (lfs_dir_t*)dir->handle));
}

bool fs_erase_all(void) {
  int ret = bd_erase_all();
  if (ret == 0) {
    LOGI("filesystem erased\ndevice must be reset, fs is in an unknown state!\n");
    return true;
  } else {
    LOGE("failed to erase filesystem");
    return false;
  }
}

static bool lock(void) {
  /* Wait for the fs to be mounted */
  if (!is_mounted) {
    rtos_semaphore_take(&mount_lock, RTOS_SEMAPHORE_TIMEOUT_MAX);
    // In case multiple threads are blocked waiting for mount_lock. We should
    // give this semaphore so that the threads block on the fs_lock semaphore.
    rtos_semaphore_give(&mount_lock);
  }

  return rtos_mutex_lock(&fs_lock);
}

static bool unlock(void) {
  return rtos_mutex_unlock(&fs_lock);
}

#ifndef EMBEDDED_BUILD
void set_lfs(void* test_lfs) {
  lfs = test_lfs;
}
#endif
