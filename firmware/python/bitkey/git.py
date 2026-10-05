import subprocess
import sys

try:
    from semver import VersionInfo
except ImportError:
    from .semver_stub import VersionInfo

from .semver_utils import validate_semver


class Git():
    _info = dict()
    _os_git_cmd = 'git'
    _tag_prefix = "fw-"

    def __init__(self) -> None:
        self._info = {
            'identity': self._get_identity(),
            'branch': self._get_branch(),
            'semver_tag': self._get_semver_tag(),
            'head_rev': self._run_git_cmd(["rev-parse", "HEAD"]),
        }

    def info(self) -> dict:
        return self._info

    @property
    def identity(self):
        return self._info['identity']

    @property
    def branch(self):
        return self._info['branch']

    @property
    def semver_tag(self):
        return self._info['semver_tag']

    @property
    def head_rev(self):
        return self._info['head_rev']

    @property
    def head_timestamp(self) -> int:
        """Return the committer timestamp for the exact HEAD revision."""
        if not self.head_rev:
            raise RuntimeError("Cannot determine HEAD commit timestamp: HEAD revision is unavailable")

        timestamp = self._run_git_cmd(
            ["show", "-s", "--format=%ct", self.head_rev]
        )
        if not timestamp:
            raise RuntimeError(
                f"Cannot determine HEAD commit timestamp for {self.head_rev}"
            )

        try:
            return int(timestamp)
        except ValueError as err:
            raise RuntimeError(
                f"Invalid HEAD commit timestamp for {self.head_rev}: {timestamp!r}"
            ) from err

    def _run_git_cmd(self, args) -> str:
        result = ''
        try:
            output = subprocess.check_output(
                [self._os_git_cmd] + args, stderr=subprocess.DEVNULL)
            result = output.decode(sys.stdout.encoding).strip()
        except Exception:
            pass
        finally:
            return result

    def _get_identity(self) -> str:
        # Git chooses the default abbreviation length from the number of objects
        # in the local repository. Pin it so full and shallow checkouts produce
        # identical firmware metadata.
        return self._run_git_cmd(
            ["describe", "--tags", "--dirty", "--long", "--abbrev=10"]
        )

    def _get_branch(self) -> str:
        return self._run_git_cmd(["rev-parse", "--abbrev-ref", "HEAD"])

    def _get_semver_tag(self) -> VersionInfo:
        latest = VersionInfo.parse("0.0.0")

        # Git failures return ""; invalid HEAD tags raise, invalid search candidates are skipped.
        current_tag_cmd = ["describe", "--tags", "--exact-match", "--match",  self._tag_prefix + "*"]
        current_tag = self._run_git_cmd(current_tag_cmd)

        if current_tag != '':
            tag = current_tag.removeprefix(self._tag_prefix)
            return VersionInfo.parse(tag)

        # Get all tags before the current commit, sorted descending by refname (most recent first)
        tags_cmd = ["tag", "--list", self._tag_prefix +
                    "*", "--sort=-v:refname", "--no-contains"]
        tags = self._run_git_cmd(tags_cmd).split('\n')

        for tag in tags:
            if not tag.startswith(self._tag_prefix):
                continue

            try:
                version = tag.removeprefix(self._tag_prefix)
                ver = validate_semver(VersionInfo.parse(version))
            except ValueError:
                continue
            latest = ver if ver > latest else latest

        return latest
