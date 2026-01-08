"""UI simulator build and run tasks"""

import click
from invoke import task
from pathlib import Path

from bitkey.meson import MesonBuild
from .lib.paths import BUILD_ROOT_DIR


@task(help={"run": "Run the UI simulator"})
def ui_sim(c, run=True):
    """Build and optionally run the UI simulator"""
    build_dir = BUILD_ROOT_DIR / "ui-sim"

    # Use MesonBuild to handle setup
    # Use "posix" platform for native builds (same as test builds)
    m = MesonBuild(c, "posix", build_dir)
    m.setup()

    # Build the ui-sim target
    c.run(f"meson compile -C {build_dir} ui-sim")

    # Run the simulator if requested
    if run:
        executable = build_dir / "ui-simulate" / "ui-simulate"
        if executable.exists():
            click.echo(click.style(
                'Running UI simulator... (Press CTRL+C to quit)', fg='green'))
            c.run(str(executable))
        else:
            click.echo(click.style(
                f'Error: Simulator executable not found at {executable}', fg='red'))
