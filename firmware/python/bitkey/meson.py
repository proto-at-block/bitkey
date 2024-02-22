import re
import sys
import json
import shlex
import pathlib
import subprocess
from shutil import copyfile

import lib.ipc.ipc_codegen as ipc_codegen

from tasks.lib.paths import *
from tasks.lib.platforms import Platforms
from enum import Enum
from functools import cached_property


def _debug_unit_test(c, test_path):
    if sys.platform != "darwin":
        print("Unit test debugging only supported on MacOS currently")
        exit(1)

    # One does not simply debug a test.
    # https://criterion.readthedocs.io/en/master/debug.html

    # https://lldb.llvm.org/use/remote.html
    test_path = BUILD_HOST_DIR.joinpath(test_path)
    debug_server = subprocess.Popen(shlex.split(
        f"{test_path} --debug"), start_new_session=True)

    # Spawn the debug client.
    # c.run(f"echo 'gdb-remote localhost:1234' | lldb {test_path}")
    c.run(f"lldb {test_path}")
    debug_server.kill()


class BuildVariant(Enum):
    DEV = "dev",
    PROD = "prod"


class Target:
    def __init__(self, target: str):
        self.target = Path(target).name

    @property
    def elf(self) -> Path:
        return Path(self.target).with_suffix(".signed.elf")

    @property
    def bin(self) -> Path:
        p = Path(self.target)
        return str(p.parent / (p.name + ".bin"))

    @property
    def variant(self) -> BuildVariant:
        if "-prod" in self.target:
            return BuildVariant.PROD
        else:
            return BuildVariant.DEV

    def loader(self, loader_name: str):
        if "app-" not in self.target:
            return None
        return Target(re.sub(r"(app-\w+)", loader_name, self.target))


class BuildOptions:
    def __init__(self, ctx, build_dir):
        self._ctx = ctx
        self._build_dir = build_dir
        self.options = """option('disable_printf', type : 'boolean', value : false)
option('config_prod', type : 'boolean', value : false)
        """

    def write(self):
        with self._ctx.cd(ROOT_DIR):
            with open("meson.options", "w") as f:
                f.write(self.options)

    def configure(self, build_variant) -> str:
        if build_variant == BuildVariant.DEV:
            opts = "-Ddisable_printf=false -Dconfig_prod=false"
        else:
            opts = "-Ddisable_printf=true -Dconfig_prod=true"
        self._ctx.run(f"meson configure {self._build_dir} {opts}")


class MesonBuild:
    def __init__(self, invoke_context, platform=None, build_dir=BUILD_FW_DIR, ignore_codegen_cache=False, target=None):
        self._ctx = invoke_context
        self._build_dir = build_dir.absolute()
        self._ignore_codegen_cache = ignore_codegen_cache
        self._platform = platform if platform else self._ctx.platform
        self._platforms = Platforms()
        self._target = target if target else self._ctx.target
        self._targets = None
        self._build_options = BuildOptions(self._ctx, self._build_dir)

    def setup(self):
        self._prebuild()
        self._setup()

    @property
    def targets(self):
        if not self._targets:
            with self._ctx.cd(self._build_dir):
                self._targets = json.loads(self._ctx.run(
                    "meson introspect --targets", hide=True).stdout)
        return self._targets

    @property
    def target(self) -> Target:
        return Target(self._target)

    @property
    def platform(self) -> dict:
        elf = self.target_path(self.target.elf)
        return self._platforms.discover(elf)

    @cached_property
    def deprecated_hw_revisions(self) -> list:
        return self.platform['deprecated_hw_revisions']

    @property
    def is_bootloader(self) -> bool:
        # This is a bit hacky, since it assumes the bl name will not be in the app target name
        return (self.platform['bootloader_image'] in self._target)

    def filter_targets(self, targets, variant: BuildVariant) -> list:
        return [t for t in targets if Target(t).variant == variant]

    def build_firmware(self, all_targets=False, verbose=False):
        targets = [str(Target(self._target).elf)
                   ] if not all_targets else self._firmware_targets

        dev_targets = self.filter_targets(targets, BuildVariant.DEV)
        prod_targets = self.filter_targets(targets, BuildVariant.PROD)

        platform_config = self._platforms.all[self._platform]

        # Build dev and prod firmware separately, since they require
        # different global options.

        if dev_targets:
            self._build_options.configure(BuildVariant.DEV)
            self._build_firmware(dev_targets, platform_config, verbose)

        if prod_targets:
            self._build_options.configure(BuildVariant.PROD)
            self._build_firmware(prod_targets, platform_config, verbose)

    def _build_firmware(self, targets, platform_config, verbose):
        with self._ctx.cd(self._build_dir):
            if platform_config["bootloader_required"]:
                loader_name = platform_config["bootloader_image"]
                loader_target = Target(self._ctx.target).loader(loader_name)
                if loader_target:
                    targets.append(str(loader_target.elf))

            targets = " ".join(targets)
            verbose = "-v" if verbose else ""
            self._ctx.run(f"meson compile {verbose} {targets}")

    def build_tests(self, target=None, verbose=False, debug=False):
        with self._ctx.cd(self._build_dir):
            verbose = "-v" if verbose else ""

            if target:
                # Build and run a specific target
                self._ctx.run(f"meson compile {verbose} {target}")
                target = self.target_path(target)
                if debug:
                    _debug_unit_test(self._ctx, target)
                else:
                    self._ctx.run(f"{target}")
            else:
                # Build all test targets
                def filter_test_targets(_target):
                    return self.filter_executables(_target, "test")

                targets = map(self._target_to_build_path, filter(
                    filter_test_targets, self.targets))
                targets = " ".join(targets)

                self._ctx.run(f"meson compile {verbose} {targets}")

    def build_fuzzers(self, verbose=False):
        with self._ctx.cd(self._build_dir):
            def filter_fuzz_targets(target):
                return self.filter_executables(target, "fuzz")

            targets = map(self._target_to_build_path, filter(
                filter_fuzz_targets, self.targets))
            targets = " ".join(targets)
            verbose = "-v" if verbose else ""

            self._ctx.run(f"meson compile {verbose} {targets}")

    def target_path(self, target=None) -> pathlib.Path:
        target = target if target else self._target
        for t in self.targets:
            if t["name"] == str(target):
                return pathlib.Path(t["filename"][0])

    def filter_executables(self, target, suffix):
        return target["type"] in ["executable"] and target["name"].endswith(suffix)

    def find_file(self, file) -> pathlib.Path:
        """Find a file in the build directory. Only use this for files which are not
        'targets' -- prefer 'target_path()' for that."""
        f = sorted(self._build_dir.glob(f"**/{file}"))
        assert len(f) == 1, f"couldn't find {file}"
        return f[0]

    @property
    def _crossfile(self):
        return f"{CONFIG_DIR}/{self._platforms.all[self._platform]['crossfile']}"

    @property
    def _firmware_targets(self):
        targets = []
        for target in self.targets:
            if target["type"] in ["executable", "custom"] and self._platform in target["defined_in"] and \
                    not any(rev in target["name"] for rev in self.deprecated_hw_revisions):
                # Multiple platforms can define targets with the same name. Meson allows for this, but
                # requires that you pass the full path to the target. We do that here:
                targets.append(self._target_to_build_path(target))
        return targets

    def _target_to_build_path(self, target):
        tgt = target["filename"][0].split(str(self._build_dir))[1]
        return tgt[1:]  # Strip leading '/'

    def _setup(self):
        if not BUILD_ROOT_DIR.exists():
            BUILD_ROOT_DIR.mkdir()
        if not self._build_dir.exists():
            self._build_options.write()
            options = ""
            cross_file_arg = ""
            if self._platform != "posix":
                cross_file_arg = f"--cross-file {self._crossfile}"
            else:
                options = ()
                if sys.platform == "darwin":
                    options = (
                        "-Db_sanitize=address -Db_lundef=false -Dc_args='-std=gnu11'")
                else:
                    options = ("-Db_sanitize=address -Db_lundef=false -Dc_args='-std=gnu11 -fprofile-instr-generate "
                               "-fcoverage-mapping' -Dcpp_args='-fprofile-instr-generate -fcoverage-mapping'")
            with self._ctx.cd(ROOT_DIR):
                self._ctx.run(
                    f"meson setup {self._build_dir} {cross_file_arg} {options}", pty=True)

    def _prebuild(self):
        # Build and relocate nanopb_pb2.py
        with self._ctx.cd(f"{ROOT_DIR}/third-party/nanopb/generator/proto/"):
            self._ctx.run("make", hide="out")

        # Run code generators
        ipc_codegen.generate_to_dir(
            IPC_GENERATED_DIR, ignore_cache=self._ignore_codegen_cache)  # See lib/ipc/README.md for rationale on not doing purely this in Meson
