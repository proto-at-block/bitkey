import os
import re
import sys
import json
import shlex
import pathlib
import subprocess
from shutil import copyfile
from typing import Optional

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
    debug_server = subprocess.Popen(
        shlex.split(f"{test_path} --debug"), start_new_session=True
    )

    # Spawn the debug client.
    # c.run(f"echo 'gdb-remote localhost:1234' | lldb {test_path}")
    c.run(f"lldb {test_path}")
    debug_server.kill()


class BuildVariant(Enum):
    DEV = "dev"
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
        if "-dev" in self.target or "mfgtest" in self.target:
            return BuildVariant.DEV
        else:
            return BuildVariant.PROD

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
option('log_tokenized', type : 'boolean', value : true,
  description : 'Emit tokenized binary log frames on UART instead of formatted ASCII (Memfault Compact Logs)')
option('enable_sysview', type : 'boolean', value : false)
option(
  'chip_id',
  type : 'string',
  value : '',
  description : 'Optional per-device chip ID (hex) for app signing'
)
        """

    def write(self):
        with self._ctx.cd(ROOT_DIR):
            with open("meson.options", "w") as f:
                f.write(self.options)

    def _normalized_chip_id(self) -> str:
        chip_id = str(getattr(self._ctx, "chip_id", "") or "").strip()
        if chip_id == "":
            return ""
        if not re.fullmatch(r"[0-9a-fA-F]+", chip_id):
            raise RuntimeError("chip_id must contain only hex characters")
        return chip_id.lower()

    def _sysview_enabled(self) -> bool:
        env_value = os.getenv("USE_SYSVIEW", "").strip().lower()
        env_enabled = env_value in {"1", "true", "yes", "on"}
        alias_enabled = bool(getattr(self._ctx, "enable_sysview", False))
        return env_enabled or alias_enabled

    def configure(
        self, build_variant, log_tokenized: bool = True, allow_sysview: bool = True
    ) -> str:
        chip_id = self._normalized_chip_id()

        # SystemView widens task MPU privilege and exposes an RTT command
        # surface, so it must never be compiled into a prod-signed image. It is
        # enabled only for dev-signed build passes (the dev app and mfgtest-dev);
        # callers building prod-signed images (the prod app and mfgtest-prod)
        # pass allow_sysview=False. When --sysview is requested for a target set
        # that spans both (e.g. `inv build.targets -p w3-uxc --sysview`), the
        # prod-signed passes are skipped instead of failing the whole build.
        requested_sysview = self._sysview_enabled()
        enable_sysview = (
            requested_sysview and allow_sysview and build_variant == BuildVariant.DEV
        )
        if requested_sysview and not enable_sysview:
            print(
                "SystemView requested but skipped for this prod-signed build "
                "pass; dev-signed images (dev / mfgtest-dev) in this build still "
                "have it enabled."
            )

        if build_variant == BuildVariant.DEV:
            opts = "-Ddisable_printf=false -Dconfig_prod=false"
        else:
            opts = "-Ddisable_printf=true -Dconfig_prod=true"

        # `log_tokenized` is a project-wide option because it changes the
        # macro expansion in every TU that includes log.h. Mfgtest variants
        # need plain ASCII over UART (factory tooling has no ELF), so callers
        # building mfgtest targets pass log_tokenized=False — this triggers a
        # full meson rebuild of the shared library .o files without
        # LOG_TOKENIZED defined, so no log_uart_emit_compact references end
        # up baked into the mfgtest binaries.
        opts = " ".join(
            [
                opts,
                f"-Dlog_tokenized={'true' if log_tokenized else 'false'}",
                f"-Denable_sysview={'true' if enable_sysview else 'false'}",
                f"-Dchip_id={shlex.quote(chip_id)}",
            ]
        )
        self._ctx.run(f"meson configure {self._build_dir} {opts}")


class MesonBuild:
    def __init__(
        self,
        invoke_context,
        platform: Optional[str] = None,
        build_dir: Optional[str] = None,
        ignore_codegen_cache: bool = False,
        target: Optional[str] = None,
        sanitize: bool = True,
    ):
        self._ctx = invoke_context
        self._ignore_codegen_cache = ignore_codegen_cache
        self._platform = platform if platform else self._ctx.platform
        self._build_dir = (
            build_dir.absolute()
            if build_dir
            else BUILD_ROOT_DIR.joinpath("firmware", self._platform).absolute()
        )
        self._platforms = Platforms()
        self._target = target if target else self._ctx.target
        self._targets = None

        # Update context to reflect the target of the build.
        self._ctx.platform = self._platform
        self._ctx.target = self._target

        self._sanitize = sanitize
        self._build_options = BuildOptions(self._ctx, self._build_dir)

    def setup(self):
        self._prebuild()
        self._setup()

    @property
    def targets(self):
        if not self._targets:
            with self._ctx.cd(self._build_dir):
                self._targets = json.loads(
                    self._ctx.run("meson introspect --targets", hide=True).stdout
                )
        return self._targets

    @property
    def target(self) -> Optional[Target]:
        return Target(self._target) if self._target else None

    @property
    def platform(self) -> dict:
        elf = self.target_path(self.target.elf)
        return self._platforms.discover(elf)

    @cached_property
    def deprecated_hw_revisions(self) -> list:
        return self.platform.get("deprecated_hw_revisions", [])

    @property
    def is_bootloader(self) -> bool:
        # This is a bit hacky, since it assumes the bl name will not be in the app target name
        return self.platform["bootloader_image"] in self._target

    def filter_targets(self, targets, variant: BuildVariant) -> list:
        return [t for t in targets if Target(t).variant == variant]

    def build_firmware(self, all_targets=False, verbose=False):
        targets = (
            [str(Target(self._target).elf)]
            if not all_targets
            else self._firmware_targets
        )

        dev_targets = self.filter_targets(targets, BuildVariant.DEV)
        prod_targets = self.filter_targets(targets, BuildVariant.PROD)

        # Mfgtest images are classified DEV (printf enabled) but must emit
        # plain ASCII so factory tooling without ELFs can read the UART.
        # Split them out as their own build pass with log_tokenized=false so
        # shared libraries rebuild without LOG_TOKENIZED — otherwise the .o
        # cache from a non-mfgtest dev build leaves log_uart_emit_compact
        # calls linked into the mfgtest binary.
        mfgtest_targets = [t for t in dev_targets if "mfgtest" in t]
        dev_targets = [t for t in dev_targets if "mfgtest" not in t]

        # mfgtest images all build with dev flags (config_prod=false), but come
        # in both dev-signed and prod-signed flavours. SystemView must stay out
        # of anything signed with the prod key, so the prod-signed mfgtest
        # images get their own pass with sysview disabled.
        mfgtest_dev_targets = [t for t in mfgtest_targets if "prod" not in t]
        mfgtest_prod_targets = [t for t in mfgtest_targets if "prod" in t]

        platform_config = self._platforms.all[self._platform]

        # Build each (variant, log_tokenized, sysview) combo separately, since
        # they require different project-wide options.

        if dev_targets:
            self._build_options.configure(BuildVariant.DEV, log_tokenized=True)
            self._build_firmware(dev_targets, platform_config, verbose, all_targets)

        if mfgtest_dev_targets:
            self._build_options.configure(BuildVariant.DEV, log_tokenized=False)
            self._build_firmware(
                mfgtest_dev_targets, platform_config, verbose, all_targets
            )

        if mfgtest_prod_targets:
            self._build_options.configure(
                BuildVariant.DEV, log_tokenized=False, allow_sysview=False
            )
            self._build_firmware(
                mfgtest_prod_targets, platform_config, verbose, all_targets
            )

        if prod_targets:
            self._build_options.configure(BuildVariant.PROD, log_tokenized=False)
            self._build_firmware(prod_targets, platform_config, verbose, all_targets)

    def _build_firmware(self, targets, platform_config, verbose, all_targets=False):
        with self._ctx.cd(self._build_dir):
            if platform_config["bootloader_required"] and not all_targets:
                loader_name = platform_config["bootloader_image"]
                loader_target = Target(self._target).loader(loader_name)
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

                targets = map(
                    self._target_to_build_path,
                    filter(filter_test_targets, self.targets),
                )
                targets = " ".join(targets)

                self._ctx.run(f"meson compile {verbose} {targets}")

    def build_fuzzers(self, verbose=False):
        with self._ctx.cd(self._build_dir):

            def filter_fuzz_targets(target):
                return self.filter_executables(target, "fuzz")

            targets = map(
                self._target_to_build_path, filter(filter_fuzz_targets, self.targets)
            )
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
            if (
                target["type"] in ["executable", "custom"]
                and self._platform in target["defined_in"]
                and not any(
                    rev in target["name"] for rev in self.deprecated_hw_revisions
                )
            ):
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
        self._build_options.write()
        if not self._build_dir.exists():
            options = ""
            cross_file_arg = ""
            if self._platform != "posix":
                cross_file_arg = f"--cross-file {self._crossfile}"
            else:
                options = ()
                sanitize_opt = "address" if self._sanitize else "none"
                if sys.platform == "darwin":
                    options = (
                        f"-Db_sanitize={sanitize_opt} -Db_lundef=false -Dc_args='-std=gnu11'"
                    )
                else:
                    options = (
                        f"-Db_sanitize={sanitize_opt} -Db_lundef=false -Dc_args='-std=gnu11 -fprofile-instr-generate "
                        "-fcoverage-mapping' -Dcpp_args='-fprofile-instr-generate -fcoverage-mapping'"
                    )
            with self._ctx.cd(ROOT_DIR):
                self._ctx.run(
                    f"meson setup {self._build_dir} {cross_file_arg} {options}",
                    pty=True,
                )
        else:
            with self._ctx.cd(ROOT_DIR):
                self._ctx.run(f"meson setup --reconfigure {self._build_dir}", pty=True)

        self._targets = None

    def _prebuild(self):
        # Build and relocate nanopb_pb2.py
        with self._ctx.cd(f"{ROOT_DIR}/third-party/nanopb/generator/proto/"):
            self._ctx.run("make", hide="out")

        # Run code generators
        ipc_codegen.generate_to_dir(
            IPC_GENERATED_DIR, ignore_cache=self._ignore_codegen_cache
        )  # See lib/ipc/README.md for rationale on not doing purely this in Meson

        if self._platform == "posix":
            # patch nfc library for posix builds
            with self._ctx.cd(ROOT_DIR):
                self._ctx.run("fuzz/patch_scripts/patch_nfc.sh")
