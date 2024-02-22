#pragma once

#include "lfs.h"

#define FS_FILE_NAME_MAX_LEN (255)

// Littlefs uses 2 blocks per superblock; 2 blocks per directory.
#define FS_BLOCK_COUNT 16
#define FS_MAX_NUM_DIR ((FS_BLOCK_COUNT - 1) / 2)

typedef struct {
  uint8_t handle[sizeof(lfs_file_t)];
  uint8_t __attribute__((aligned(4))) file_buffer[8 * 1024];
} fs_file_t;

typedef struct {
  uint8_t handle[sizeof(lfs_dir_t)];
} fs_dir_t;

typedef enum {
  FS_FILE_TYPE_ERR = -1,
  FS_FILE_TYPE_REG = 1,
  FS_FILE_TYPE_DIR = 2,
} fs_filetype_t;

typedef struct {
  fs_filetype_t type;
  uint32_t size;  // only valid for REG files.
  char name[FS_FILE_NAME_MAX_LEN + 1];
} fs_dir_info_t;

// File open flags
typedef enum {
  FS_O_RDONLY = 1,       // Open a file as read only
  FS_O_WRONLY = 2,       // Open a file as write only
  FS_O_RDWR = 3,         // Open a file as read and write
  FS_O_CREAT = 0x0100,   // Create a file if it does not exist
  FS_O_EXCL = 0x0200,    // Fail if a file already exists
  FS_O_TRUNC = 0x0400,   // Truncate the existing file to zero size
  FS_O_APPEND = 0x0800,  // Move to end of file on every write
} fs_open_flags_t;

// File seek flags
typedef enum {
  FS_SEEK_SET = 0,  // Seek relative to an absolute position
  FS_SEEK_CUR = 1,  // Seek relative to the current file position
  FS_SEEK_END = 2,  // Seek relative to the end of the file
} fs_whence_flags_t;

void fs_mount(void);
int32_t fs_used(void);
char* fs_error_str(const int error);

int fs_open(fs_file_t* file, const char* path, int flags);
int fs_close(fs_file_t* file);

int fs_open_global(fs_file_t** file, const char* path, int flags);
int fs_close_global(fs_file_t* file);

int fs_remove(const char* path);
int fs_rename(const char* oldpath, const char* newpath);
bool fs_file_exists(const char* path);
int fs_get_filesize(const char* path);
fs_filetype_t fs_get_filetype(const char* path);
int fs_touch(const char* path);
int fs_file_sync(fs_file_t* file);
int32_t fs_file_read(fs_file_t* file, void* buffer, uint32_t size);
int32_t fs_file_write(fs_file_t* file, const void* buffer, uint32_t size);
int32_t fs_file_seek(fs_file_t* file, int32_t off, fs_whence_flags_t whence);
int fs_file_truncate(fs_file_t* file, int32_t size);
int32_t fs_file_tell(fs_file_t* file);
int fs_file_rewind(fs_file_t* file);
int32_t fs_file_size(fs_file_t* file);

int fs_mkdir(const char* path);
int fs_ensure_file_path(const char* path);
int fs_dir_open(fs_dir_t* dir, const char* path);
int fs_dir_close(fs_dir_t* dir);
int fs_dir_read(fs_dir_t* dir, fs_dir_info_t* info);
int fs_dir_seek(fs_dir_t* dir, int32_t off);
int32_t fs_dir_tell(fs_dir_t* dir);
int fs_dir_rewind(fs_dir_t* dir);

bool fs_erase_all(void);

// Write data to the file specified by filename using the global fs_file_t.
bool fs_util_write_global(char* filename, uint8_t* data, uint32_t size);

// Read data from the file specified by filename using the global fs_file_t.
// `data` is managed by the caller.
bool fs_util_read_global(char* filename, uint8_t* data, uint32_t size);

// Like fs_util_read_global, but returns the number of bytes read in `size_out`.
bool fs_util_read_all_global(char* filename, uint8_t* data, uint32_t max_size, uint32_t* size_out);

#ifndef EMBEDDED_BUILD
// Test only.
void set_lfs(void* test_lfs);
#endif
