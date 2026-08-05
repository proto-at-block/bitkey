from pathlib import Path

TASKS_DIR = Path(__file__).parent.parent.absolute()
ROOT_DIR = TASKS_DIR.parent.absolute()
APPS_DIR = ROOT_DIR.joinpath("app")
BUILD_ROOT_DIR = ROOT_DIR.joinpath("build")
BUILD_HOST_DIR = BUILD_ROOT_DIR.joinpath("host")
BUILD_FW_DIR = BUILD_ROOT_DIR.joinpath("firmware")
BUILD_FWUP_BUNDLE_DIR = BUILD_ROOT_DIR.joinpath("fwup-bundle")
CONFIG_FILE = ROOT_DIR.joinpath("invoke.json")
CONFIG_FILE_TASKS = TASKS_DIR.joinpath("invoke.json")
CONFIG_DIR = ROOT_DIR.joinpath("config")
PLATFORM_FILE = ROOT_DIR.joinpath("config", "platforms.yaml")
IPC_GENERATED_DIR = ROOT_DIR.joinpath("lib/ipc/generated")
PROTO_BUILD_DIR = ROOT_DIR.joinpath("lib/protobuf/build")
GENERATED_CODE_DIRS = [IPC_GENERATED_DIR, PROTO_BUILD_DIR]
FS_BACKUPS = ROOT_DIR.joinpath(".fs-backups")
AUTOMATION_DIR = ROOT_DIR.joinpath("python/automation")

COMMANDER_BIN = "/Applications/Commander.app/Contents/MacOS/commander"
