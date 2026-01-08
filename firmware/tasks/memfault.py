import os
import base64
import json
import sh
import requests
from pathlib import Path
import tempfile
import time
import click
import shutil
from invoke import task, exceptions
from typing import Tuple

import plotly.express as px
import pandas
import pprint

from prettytable import PrettyTable
from dataclasses import dataclass
from bitkey.meson import MesonBuild
from bitkey.walletfs import GDBFs
from .lib.config import update_config
from bitkey.git import Git

COHORTS = ["default", "bitkey-team", "bitkey-external-beta"]


def bearer_token():
    token = os.environ.get("MEMFAULT_ORG_TOKEN")
    if token is None:
        raise ValueError("MEMFAULT_ORG_TOKEN environment variable is not set")
    return token


def auth_headers():
    token = bearer_token()
    auth = f":{token}"
    token = base64.b64encode(auth.encode("utf-8")).decode("utf-8")
    return {
        "Authorization": f"Basic {token}",
    }


@task(help={"target": "Build target to backup"})
def coredump(ctx, target=None):
    target = target if target else ctx.target

    coredumps_filename = "coredumps.bin"
    issue_url = f"https://app.memfault.com/organizations/{ctx.memfault_org}/projects/{ctx.memfault_project}/issues/"

    # Check for saved memfault login
    check_login(ctx)

    click.echo("Reading filesystem memory")
    try:
        gdbfs = GDBFs(ctx, target=target)
        fs = gdbfs.fetch()
    except:
        click.echo(click.style(f"Unable to read device filesystem", fg="red"))
        raise exceptions.Exit(code=1)

    click.echo("Extracting coredump")
    try:
        cd = fs.read_file(coredumps_filename)
        if cd is None:
            click.echo(click.style(
                f"{coredumps_filename} not found", fg="red"))
            raise exceptions.Exit(code=1)
    except:
        click.echo(click.style(
            f"Unable to read {coredumps_filename}", fg="red"))
        raise exceptions.Exit(code=1)

    # Get the last trace id, before we upload the coredump

    try:
        click.echo("Getting device serial number and most recent trace id")
        serial = fs.get_serial() or "XXXXXXXXXXXXXXXX"
        click.echo("Getting most recent trace id")
        (previous_trace_id, previous_trace_count) = find_latest_trace_id(
            ctx, "coredump", serial
        )
    except Exception as e:
        click.echo(click.style(f"Error {e}", fg="red"))
        raise exceptions.Exit(code=1)

    # Upload the coredump
    upload_symbols = True
    click.echo("Uploading coredump")
    try:
        with tempfile.NamedTemporaryFile(mode="wb") as tmp:
            tmp.write(cd.getvalue())
            tmp.flush()

            # Upload coredump
            upload_coredump_cmd = [
                "memfault",
                "--org-token",
                f"{ctx.memfault_org_token}",
                "--org",
                f"{ctx.memfault_org}",
                "--project",
                f"{ctx.memfault_project}",
                "upload-coredump",
                "--device-serial",
                f"{serial}",
                f"{tmp.name}",
            ]
            result = ctx.run(" ".join(upload_coredump_cmd), hide=True)

            if "was already uploaded" in result.stdout:
                upload_symbols = False
    except:
        click.echo(
            click.style(
                f"Unable to upload coredump {coredumps_filename}", fg="red")
        )
        raise exceptions.Exit(code=1)

    # Upload symbol files
    if upload_symbols:
        click.echo("Uploading mcu symbols")
        try:
            git = Git()
            sw_type = Path(target).stem.replace(f"{ctx.memfault_project}-", "")
            mb = MesonBuild(ctx, target=target)
            symbols = mb.target_path(mb.target.elf)
            version = f"{git.semver_tag}-{git.identity}-{int(time.time())}"
            upload_symbols_cmd = [
                "memfault",
                "--org-token",
                f"{ctx.memfault_org_token}",
                "--org",
                f"{ctx.memfault_org}",
                "--project",
                f"{ctx.memfault_project}",
                "upload-mcu-symbols",
                "--software-type",
                f"{sw_type}",
                "--software-version",
                f"{version}",
                "--revision",
                f"{git.head_rev}",
                f"{symbols}",
            ]
            ctx.run(" ".join(upload_symbols_cmd), hide=True)
        except:
            click.echo(click.style(f"Unable to upload mcu symbols", fg="red"))
            raise exceptions.Exit(code=1)
    else:
        click.echo(f"Coredump already uploaded, skipping mcu symbol upload")
        click.echo(
            click.style(
                f'View the coredump here: {issue_url + f"{previous_trace_id}"}',
                fg="green",
            )
        )
        raise exceptions.Exit(code=1)
        return

    # It takes some time for memfault to process the coredump,
    # so we keep track of the latest trace id before the codedump upload,
    # then attempt to find a new trace id once a second for 5 seconds
    try:
        click.echo("Getting new trace id")
        for _ in range(10):
            # Find the latest coredump
            (trace_id, trace_count) = find_latest_trace_id(
                ctx, "coredump", serial)

            if (
                trace_id is not None
                and trace_id != previous_trace_id
                or (
                    trace_id == previous_trace_id and trace_count > previous_trace_count
                )
            ):
                click.echo(
                    click.style(
                        f'View the coredump here: {issue_url + f"{trace_id}"}',
                        fg="green",
                    )
                )
                return

            time.sleep(1)
    except:
        pass

    click.echo(click.style(f"Unable to find trace on Memfault.", fg="red"))
    click.echo(
        f'Coredump may be viewable here: {issue_url + f"{previous_trace_id}"}')


def find_latest_trace_id(ctx, type, serial) -> Tuple[int, int]:
    """Finds the latest trace ID for a type and serial number"""

    url = f"https://api.memfault.com/api/v0/organizations/{ctx.memfault_org}/projects/{ctx.memfault_project}/issues"
    auth = f":{ctx.memfault_org_token}"
    token = base64.b64encode(auth.encode("utf-8")).decode("utf-8")
    headers = {"cache-control": "no-cache", "Authorization": f"Basic {token}"}

    response = requests.get(url, headers=headers)
    data = json.loads(response.text)

    for issue in data["data"]:
        if (last_trace := issue["last_trace"])[
            "source_type"
        ] == type and serial == last_trace["device"]["device_serial"]:
            return (last_trace["issue_id"], issue["trace_count"])

    return (None, None)


def check_login(ctx):
    if (
        "memfault_user" not in ctx
        or "memfault_key" not in ctx
        or "memfault_org_token" not in ctx
        or ctx.memfault_user == ""
        or ctx.memfault_key == ""
        or ctx.memfault_org_token == ""
    ):
        click.echo(click.style(f"Memfault login is required", fg="yellow"))
        save_login(ctx)


def save_login(ctx):
    config = {}
    if "memfault_user" not in ctx or ctx.memfault_user == "":
        config["memfault_user"] = click.prompt(
            "Memfault Email address", type=str)

    if "memfault_key" not in ctx or ctx.memfault_key == "":
        click.echo(
            click.style(
                "Generate a user API key here: https://app.memfault.com/profile",
                fg="green",
            )
        )
        config["memfault_key"] = click.prompt(
            "Memfault User API Key", type=str, hide_input=True
        )

    if "memfault_org_token" not in ctx or ctx.memfault_org_token == "":
        click.echo(
            click.style(
                "Create an organization token named 'Invoke: yourname' here:\nhttps://app.memfault.com/organizations/block-wallet/settings/auth-tokens",
                fg="green",
            )
        )
        config["memfault_org_token"] = click.prompt(
            "Memfault Org Token", type=str, hide_input=True
        )

    config["memfault_org"] = "block-wallet"
    config["memfault_project"] = "w1a"
    update_config(config)
    ctx.update(config)


@task()
def create_device(ctx, device_serial, hw_version, cohort="default"):
    url = f"https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/devices"

    payload = {
        "device_serial": device_serial,
        "hardware_version": hw_version,
        "cohort": cohort,
    }

    click.echo(requests.post(url, headers=auth_headers(), json=payload))


@task()
def check_coredump_status(ctx, identifier, project_key):
    url = f"https://api.memfault.com/api/v0/queue/{identifier}"

    headers = {
        "Memfault-Project-Key": f"{project_key}",
    } | auth_headers()

    click.echo(requests.get(url, headers=headers))


@task
def released_versions(ctx, quiet=False):
    url = f"https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/releases?per_page=10000"

    releases = json.loads(requests.get(
        url, headers=auth_headers()).text)["data"]
    output = []
    for release in releases:
        click.echo(release)
        output.append(release["version"])

    if not quiet:
        click.echo(output)

    return output


@task
def fetch_release(ctx, version, hw_revision, sw_type, output_dir) -> Path:
    """Fetch a release and extract the bundle to `output_dir`.
    Will delete any previously existing files for the same (version, hw_revision, sw_type) triple,
    if they are present in `output_dir`.
    """
    url = f"https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/releases/{version}"
    template = f"fwup-bundle-{version}-{hw_revision}-{sw_type}"

    def download_and_extract(zip_contents):
        o = Path(output_dir)
        zip_path = o.joinpath(template + ".zip")
        zip_path.unlink(missing_ok=True)

        with open(zip_path, "wb") as f:
            f.write(zip_contents)
        bundle_path = o.joinpath(template)

        if bundle_path.exists():
            shutil.rmtree(bundle_path)

        shutil.unpack_archive(zip_path, extract_dir=bundle_path)

        return bundle_path

    rsp = json.loads(requests.get(url, headers=auth_headers()).text)["data"]
    for artifact in rsp["artifacts"]:
        if (
            artifact["hardware_version"]["name"] == hw_revision
            and artifact["hardware_version"]["primary_software_type"]["name"] == sw_type
        ):
            return download_and_extract(requests.get(artifact["url"]).content)

    click.echo(f"No release found for {hw_revision}, {sw_type}, {version}")
    return None


def _list_issues():
    url = f"https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/issues?per_page=10000"
    return json.loads(requests.get(url, headers=auth_headers()).text)["data"]


@task
def list_issues(
    ctx,
):
    print(
        _list_issues(
            ctx,
        )
    )


@task
def fingerprint_issue_tracker(ctx, no_graph=False):
    all_issues = _list_issues()

    pass_reason = "Bio Enroll Sample Pass Count"
    fail_reason = "Bio Enroll Sample Fail Count"
    title = "Bio Enroll Sample"

    pass_count = 0
    fail_count_map = {}
    for issue in all_issues:
        if title in issue["title"]:
            count = issue["trace_count"]
            if issue["reason"] == pass_reason:
                status_code = int(issue["title"].split("/ ")[1].rstrip("]"))
                pass_count += count
            if issue["reason"] == fail_reason:
                status_code = int(issue["title"].split("/ ")[1].rstrip("]"))
                try:
                    fail_count_map[status_code] += count
                except:
                    fail_count_map[status_code] = count

    if no_graph:
        print(f"Successful enrollments: {pass_count}")
        print(f"Failed samples per enrollment:")
        pprint.pprint(fail_count_map, width=1)
    else:
        data_frame = pandas.DataFrame(
            list(fail_count_map.items()),
            columns=["Failed samples per enrollment", "Count"],
        )
        figure = px.bar(
            data_frame, x="Failed samples per enrollment", y="Count")
        figure.show()


@dataclass
class DeploymentView:
    """Wrapper around raw Memfault deployment, displaying only certain fields."""

    deployed_date: str
    updated_date: str
    created_date: str
    cohort: str
    deployer_email: str
    type: str
    version: str
    from_version: str
    to_version: str


def get_deployments():
    """Retrieve all active deployments from Memfault, and return them as a tuple of (delta, normal) releases."""
    url = f"https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/deployments"

    deployments = json.loads(requests.get(
        url, headers=auth_headers()).text)["data"]

    delta = []
    normal = []

    for d in deployments:
        if d["status"] == "pulled":
            continue

        try:
            release_type = d["release"]["type"]
        except:
            release_type = "normal"

        try:
            email = d["deployer"]["email"]
        except:
            email = "CLI"

        view = DeploymentView(
            deployed_date=d["deployed_date"],
            updated_date=d["updated_date"],
            created_date=d["created_date"],
            cohort=d["cohort"]["name"],
            deployer_email=email,
            type=release_type,
            version="",
            from_version="",
            to_version="",
        )

        if view.type == "delta":
            view.from_version = d["release"]["from_version"]["version"]
            view.to_version = d["release"]["to_version"]["version"]
            delta.append(view)
        else:
            view.version = d["release"]["version"]
            normal.append(view)

    return delta, normal


@task
def list_deployments(ctx, version=None):
    delta, normal = get_deployments()

    table = PrettyTable()
    table.field_names = [
        "Deployed Date",
        "Cohort",
        "Deployer Email",
        "Type",
        "Version",
        "From Version",
        "To Version",
    ]

    for d in normal:
        if version is None or version == d.version:
            table.add_row(
                [
                    d.deployed_date,
                    d.cohort,
                    d.deployer_email,
                    d.type,
                    d.version,
                    d.from_version,
                    d.to_version,
                ]
            )

    click.echo(click.style("Full deployments\n", fg="blue", bold=True))
    print(table)

    table.clear_rows()

    for d in delta:
        if version is None or version == d.to_version:
            table.add_row(
                [
                    d.deployed_date,
                    d.cohort,
                    d.deployer_email,
                    d.type,
                    d.version,
                    d.from_version,
                    d.to_version,
                ]
            )

    click.echo(click.style("\nDelta deployments\n", fg="blue", bold=True))
    print(table)


def get_delta_releases(ctx, from_version=None, to_version=None):
    """Get all delta releases; optionally filter by from version."""
    url = f"https://api.memfault.com/api/v0/organizations/block-wallet/projects/w1a/delta-releases?per_page=10000"

    releases = json.loads(requests.get(
        url, headers=auth_headers()).text)["data"]

    if from_version:
        releases = [r for r in releases if r["from_version"]
                    ["version"] == from_version]
    elif to_version:
        releases = [r for r in releases if r["to_version"]
                    ["version"] == to_version]

    return releases


@task
def list_delta_releases(ctx, from_version=None, to_version=None):
    table = PrettyTable()
    table.field_names = ["From Version", "To Version"]

    for release in get_delta_releases(ctx, from_version, to_version):
        table.add_row(
            [release["from_version"]["version"], release["to_version"]["version"]]
        ),

    print(table)


@task
def activate_delta_release(
    ctx, to_version, cohort, from_version=None, deactivate=False, percent=0, dry_run=False
):
    """Activate (or deactivate) a delta release for a given cohort.

    `to_version` is the version to activate, and must be supplied.
    If `from_version` is not supplied, all possible delta releases for the `to_version` will be activated.
    For example, if `to_version` is 1.0.65, and `from_version` is not set, then all releases to 1.0.65 will be activated:
        1.0.64 -> 1.0.65
        1.0.63 -> 1.0.65
        1.0.62 -> 1.0.65
        ... etc. ...

    If `from_version` is set, then *only* that delta release will be activated. For example, if `from_version` is 1.0.64,
    then only 1.0.64 -> 1.0.65 will be activated.
    """
    releases = get_delta_releases(
        ctx, from_version=from_version, to_version=to_version)

    if cohort == "bitkey-external":
        # We changed the cosmetic name in Memfault, but it doesn't change the real name
        cohort = "bitkey-external-beta"

    assert cohort in COHORTS
    assert percent > 0

    for release in releases:
        args = [
            "--org-token",
            bearer_token(),
            "--org",
            "block-wallet",
            "--project",
            "w1a",
            "deploy-release",
            "--delta-from",
            release["from_version"]["version"],
            "--delta-to",
            release["to_version"]["version"],
            "--cohort",
            cohort,
            "--rollout-percent",
            str(percent),
        ]
        if deactivate:
            args.append("--deactivate")

        if dry_run:
            print(args)
        else:
            try:
                verb = "Deactivating" if deactivate else "Activating"
                click.echo(
                    click.style(
                        f"{verb} {release['from_version']['version']} -> {release['to_version']['version']} to cohort {cohort}",
                        fg="green",
                    )
                )
                click.echo(sh.memfault(*args))
            except sh.ErrorReturnCode as e:
                error_msg = e.stderr.decode("utf-8")
                if "already active" in error_msg.lower():
                    click.echo(
                        click.style(
                            f"  Release already active, deactivating first...",
                            fg="yellow",
                        )
                    )
                    deactivate_args = args.copy()
                    deactivate_args.append("--deactivate")
                    sh.memfault(*deactivate_args)
                    click.echo(sh.memfault(*args))
                else:
                    click.echo(click.style(error_msg, fg="red"))
