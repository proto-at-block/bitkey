import re
import sys
from pathlib import Path
from typing import Optional

import click

from . import constants, runner


def validate_stack_name(ctx, param, value):
    """Validate that stack_name contains only alphanumeric characters."""
    if value is not None and not re.match(r"^[a-zA-Z0-9]+$", value):
        raise click.BadParameter(
            "stack-name must contain only alphanumeric characters (a-zA-Z0-9)"
        )
    return value


@click.group("bitkey-fwa")
@click.pass_context
def cli(ctx):
    """Bitkey firmware analysis tool."""
    pass


@cli.command("analyze", short_help="Run firmware analysis")
@click.option(
    "-i",
    "--input",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to the firmware under test",
)
@click.option(
    "-o",
    "--output",
    default=None,
    type=click.Path(path_type=Path),
    help="Path to store test results",
)
@click.option(
    "-s",
    "--signer-env",
    default=constants.SIGNER_PERSONAL,
    type=click.Choice(constants.SIGNER_ENVS),
    help="Used by firmware signing service for app cert verification",
)
@click.option(
    "--stack-name",
    default=None,
    callback=validate_stack_name,
    help="Personal dev stack name (alphanumeric only). Implies --signer-env development.",
)
@click.option("-d", "--dry-run", is_flag=True, help="List tests, but do not execute")
@click.option("-v", "--verbose", is_flag=True, help="Verbose output")
def analyze(
    input: Path,
    output: Optional[Path],
    signer_env: constants.SIGNER_ENVS,
    stack_name: Optional[str],
    dry_run: bool,
    verbose: bool,
):
    """This command is used to execute security tests over the given firmware."""

    if stack_name:
        signer_env = constants.SIGNER_DEVELOPMENT

    success = runner.run_analysis(
        input, output, signer_env, stack_name, dry_run, verbose
    )
    if not success:
        exit(1)


@cli.command(
    "bulk-analyze", short_help="Perform bulk firmware analysis over a given directory"
)
@click.option(
    "-q", "--quiet", is_flag=True, help="Suppress console reports, only show status"
)
def bulk_analyze(quiet: bool):
    """Perform bulk firmware analysis from the root fw dir"""
    stream = sys.stdout
    if quiet:
        stream = None

    success = runner.run_bulk_analysis(stream=stream)
    if not success:
        exit(1)


if __name__ == "__main__":
    cli(prog_name="bitkey-fwa")
