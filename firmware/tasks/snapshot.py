"""Snapshot testing tasks for UI visual regression testing."""

import glob
import platform
import shlex
from invoke import task, Exit

from bitkey.meson import MesonBuild

from .lib.paths import BUILD_HOST_DIR, ROOT_DIR


@task(help={"force": "Regenerate all images even if they exist"})
def gen(c, force=False):
    """Generate/regenerate snapshot golden images.

    Builds and runs ui-snapshot-gen which generates golden PNG images
    for all UI screens. Use --force to regenerate existing images.
    """
    m = MesonBuild(c, "posix", BUILD_HOST_DIR)
    m.setup()

    with c.cd(BUILD_HOST_DIR):
        c.run("meson compile app/ui-snapshot/ui-snapshot-gen")

    with c.cd(ROOT_DIR):
        if force:
            c.run(f"{BUILD_HOST_DIR}/app/ui-snapshot/ui-snapshot-gen --force")
        else:
            c.run(f"{BUILD_HOST_DIR}/app/ui-snapshot/ui-snapshot-gen")


@task
def verify(c):
    """Verify snapshots match golden images (CI mode).

    Runs the snapshot tests which compare rendered UI against golden
    images. Fails if any golden is missing or mismatches.

    On macOS, uses the generator in verify mode due to Criterion
    protocol errors. On Linux, runs the full test suite.
    """
    # 1. Check that snapshot PNGs exist
    pngs = glob.glob(str(ROOT_DIR / "test/snapshots/**/*.png"), recursive=True)
    if not pngs:
        raise Exit("No snapshot files found. Run: inv snapshot.gen")

    # 2. Check LFS files are actual images, not pointers
    # LFS pointer files start with "version https://git-lfs.github.com/spec/v1"
    # Check each file individually to avoid shell argument limits
    for png in pngs:
        result = c.run(f"file {shlex.quote(png)}", hide=True)
        if "PNG image" not in result.stdout:
            raise Exit(f"LFS file not downloaded: {png}. Run: git lfs pull")

    m = MesonBuild(c, "posix", BUILD_HOST_DIR)
    m.setup()

    # 3. Run tests
    if platform.system() == "Darwin":
        # macOS: Criterion has protocol errors, use generator in verify mode
        with c.cd(BUILD_HOST_DIR):
            c.run("meson compile app/ui-snapshot/ui-snapshot-gen")
        with c.cd(ROOT_DIR):
            # Generator verifies existing images, fails if any don't match
            c.run(f"{BUILD_HOST_DIR}/app/ui-snapshot/ui-snapshot-gen")
    else:
        # Linux: Run full test suite via meson
        with c.cd(BUILD_HOST_DIR):
            c.run("meson compile app/ui-snapshot/ui-snapshot-test")
            c.run("meson test 'ui snapshot test' -v")

    # 4. Secondary guard: check no files were modified
    with c.cd(ROOT_DIR):
        c.run("git diff --exit-code test/snapshots/")
