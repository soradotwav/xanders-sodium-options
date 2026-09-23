#!/usr/bin/env python3
"""Release preparation and publishing. Python 3.11+, standard library only.

Network writes occur in selectors, prepare, stage, publish, and recover-curseforge.
The plan command is offline and never reads publishing credentials.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.(0|[1-9]\d*))?")
SELECTORS = ("fix", "minor", "major")
MR_API = "https://api.modrinth.com/v2"
CF_API = "https://minecraft.curseforge.com/api"
MINECRAFT_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
MODRINTH_PROJECT = "fGB38jk4"
CURSEFORGE_PROJECT = 1454606
# Deliberate public relationships, not every compile/runtime integration.
PUBLISH_RELATIONS = (
    {"modrinth": "AANobbMI", "curseforge": "sodium", "type": "required"},
    {"modrinth": "Bh37bMuy", "curseforge": "reeses-sodium-options", "type": "incompatible"},
)
FABRIC_API_RELATION = {"modrinth": "P7dR8mSH", "curseforge": "fabric-api", "type": "required"}


class ReleaseError(Exception):
    pass


class ApiError(ReleaseError):
    def __init__(self, status, host):
        self.status = status
        super().__init__(f"{host} returned HTTP {status}; no credentials or response body were logged")


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def git(*args, env=None):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True, env=env).strip()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def version_key(value):
    match = VERSION.fullmatch(value)
    require(match, f"Invalid version: {value!r}; use X.Y.Z or X.Y.Z-alpha.N/beta.N/rc.N")
    major, minor, patch, channel, number = match.groups()
    return (int(major), int(minor), int(patch),
            {"alpha": 0, "beta": 1, "rc": 2, None: 3}[channel], int(number or 0))


def next_version(current, bump):
    major, minor, patch, *_ = version_key(current)
    require(bump in SELECTORS, "Choose fix, minor or major")
    value = {"fix": f"{major}.{minor}.{patch + 1}",
             "minor": f"{major}.{minor + 1}.0", "major": f"{major + 1}.0.0"}[bump]
    require(version_key(value) > version_key(current), "New version must exceed mod.version")
    return value


def minecraft_versions(args=None):
    manifest = request(MINECRAFT_MANIFEST)
    versions = [v["id"] for v in manifest["versions"]
                if v["type"] == "release" and re.fullmatch(r"\d+(?:\.\d+)+", v["id"])]
    require(versions, "Minecraft release catalog is empty")
    write_json(ROOT / "build/release/minecraft-versions.json", versions)


def changelog_for(body, loader):
    """Plain Markdown is shared. Reserved H2s select additions or full overrides."""
    labels = {"Shared", "Fabric", "NeoForge", "Fabric override", "NeoForge override"}
    sections = {"Shared": []}
    seen = set()
    current = "Shared"
    fence = None
    for line in body.splitlines():
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            token = marker[1][0]
            fence = None if fence == token else (token if fence is None else fence)
        heading = re.fullmatch(r"## (.+?)\s*", line) if fence is None else None
        if heading and heading[1] in labels:
            current = heading[1]
            require(current not in seen, f"Duplicate changelog section: {current}")
            seen.add(current)
            sections.setdefault(current, [])
        else:
            sections.setdefault(current, []).append(line)
    label = {"fabric": "Fabric", "neoforge": "NeoForge"}[loader]
    override = sections.get(label + " override")
    if override is not None:
        result = "\n".join(override).strip()
    else:
        result = "\n\n".join("\n".join(sections.get(k, [])).strip()
                              for k in ("Shared", label)).strip()
    require(re.sub(r"<!--.*?-->", "", result, flags=re.S).strip(),
            f"Write release notes for {loader} before publishing")
    return result


def output(**values):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as handle:
            for key, value in values.items():
                require("\n" not in str(value), "Multiline workflow output")
                handle.write(f"{key}={value}\n")


def request(url, method="GET", payload=None, headers=None, raw=False, missing_ok=False):
    headers = {"User-Agent": "soradotwav/xanders-sodium-options release workflow", **(headers or {})}
    if isinstance(payload, (dict, list)):
        payload = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    try:
        with urlopen(Request(url, data=payload, headers=headers, method=method), timeout=120) as response:
            body = response.read()
            return body if raw else (json.loads(body) if body else None)
    except HTTPError as error:
        if missing_ok and error.code == 404:
            return None
        raise ApiError(error.code, url.split("/")[2]) from None
    except (URLError, TimeoutError):
        raise ReleaseError(f"No confirmed response from {url.split('/')[2]}; inspect any pending upload before retrying") from None


def repo():
    name = os.environ.get("GITHUB_REPOSITORY", "")
    require(re.fullmatch(r"[\w.-]+/[\w.-]+", name), "GITHUB_REPOSITORY is missing or invalid")
    return name


def gh_headers():
    token = os.environ.get("GH_TOKEN", "")
    require(token, "GH_TOKEN is required")
    return {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28"}


def github(path, **kwargs):
    base = f"https://api.github.com/repos/{repo()}"
    return request(base + ("/" + path if path else ""), headers=gh_headers(), **kwargs)


def asset_bytes(asset):
    return request(asset["url"], headers={**gh_headers(), "Accept": "application/octet-stream"}, raw=True)


def assets(release_id):
    found = {}
    page = 1
    while True:
        batch = github(f"releases/{release_id}/assets?per_page=100&page={page}")
        for asset in batch:
            found[asset["name"]] = asset
        if len(batch) < 100:
            return found
        page += 1


def upload_asset(release_id, name, data, content_type="application/json"):
    return request(f"https://uploads.github.com/repos/{repo()}/releases/{release_id}/assets?" +
                   urlencode({"name": name}), method="POST", payload=data,
                   headers={**gh_headers(), "Content-Type": content_type})


def set_version(root, version):
    version_key(version)
    path = root / "gradle.properties"
    text, count = re.subn(r"(?m)^mod\.version=.*$", "mod.version=" + version, path.read_text())
    require(count == 1, "Expected one mod.version property")
    path.write_text(text)


def validate_version(tag, root=ROOT):
    require(tag.startswith("v") and VERSION.fullmatch(tag[1:]), "Invalid version tag")
    version = tag[1:]
    require(properties(root / "gradle.properties")["mod.version"] == version, "Tag and mod.version disagree")
    return version


def bot_environment(timestamp):
    return {**os.environ, "GIT_AUTHOR_NAME": "github-actions[bot]",
            "GIT_COMMITTER_NAME": "github-actions[bot]",
            "GIT_AUTHOR_EMAIL": "41898282+github-actions[bot]@users.noreply.github.com",
            "GIT_COMMITTER_EMAIL": "41898282+github-actions[bot]@users.noreply.github.com",
            "GIT_AUTHOR_DATE": timestamp, "GIT_COMMITTER_DATE": timestamp}


def ancestor(older, newer):
    result = subprocess.run(["git", "merge-base", "--is-ancestor", older, newer], cwd=ROOT)
    require(result.returncode in (0, 1), "Cannot establish release ancestry")
    return result.returncode == 0


def make_version_commit(state):
    # A fixed identity/date makes recovery recreate exactly the same commit even
    # if the runner stops after saving the state but before pushing to main.
    require(not git("status", "--porcelain"), "Release preparation requires a clean checkout")
    git("checkout", "--detach", state["source_sha"])
    require(properties(ROOT / "gradle.properties")["mod.version"] == state["previous"],
            "Source version changed")
    set_version(ROOT, state["version"])
    git("add", "gradle.properties")
    git("-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "-m",
        f"[chore] release {state['version']}", env=bot_environment(state["timestamp"]))
    return git("rev-parse", "HEAD")


def push_version_commit(state):
    branch = state["branch"]
    git("fetch", "origin", f"+refs/heads/{branch}:refs/remotes/origin/{branch}")
    head = git("rev-parse", "origin/" + branch)
    if ancestor(state["sha"], head):
        return  # The earlier attempt already committed the bump.
    require(ancestor(state["source_sha"], head), "Default branch history changed; refusing to overwrite it")
    if head != state["source_sha"]:
        # Preserve commits merged while this release was being prepared. The
        # release tag still identifies the original, now-versioned snapshot.
        git("checkout", "--detach", head)
        require(properties(ROOT / "gradle.properties")["mod.version"] == state["previous"],
                "Another change bumped mod.version; resolve it before retrying this release")
        git("-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "merge", "--no-ff",
            "-m", f"[chore] merge {state['version']} release version", state["sha"],
            env=bot_environment(state["timestamp"]))
    else:
        git("checkout", "--detach", state["sha"])
    # No force push. If main advances again, a rerun safely retries the merge.
    git("push", "origin", f"HEAD:refs/heads/{branch}")


def check_release(release, state=None):
    require(not release["draft"], "Publish the GitHub release before running uploads")
    require(not release.get("immutable", False), "Disable immutable releases before using selector tags")
    require(isinstance(release.get("name"), str) and release["name"].strip(), "Enter a release title")
    for loader in ("fabric", "neoforge"):
        changelog_for(release.get("body") or "", loader)
    if state is not None:
        require(state["repository"] == repo() and state["release_id"] == release["id"], "Release state belongs to another release")
        require(state["selector"] in SELECTORS and state["tag"] == "v" + state["version"], "Invalid release state")
        require(next_version(state["previous"], state["selector"]) == state["version"], "Invalid saved version bump")
        require(all(re.fullmatch(r"[0-9a-f]{40}", state[k]) for k in ("sha", "source_sha")), "Invalid release commit")
        require(release["tag_name"] in (state["selector"], state["tag"]), "Release tag changed unexpectedly")
        require(all(release[k] == state[k] for k in ("name", "body", "prerelease")),
                "Title, notes or release type changed after preparation; restore them before retrying")


def prepare(args):
    require(args.release_id > 0, "A positive GitHub release ID is required")
    release = github(f"releases/{args.release_id}")
    check_release(release)
    default = github("")["default_branch"]
    git("fetch", "origin", f"+refs/heads/{default}:refs/remotes/origin/{default}")
    current = assets(release["id"])
    if "release-state.json" in current:
        state = json.loads(asset_bytes(current["release-state.json"]))
        check_release(release, state)
        require(state["branch"] == default, "Default branch changed during release")
        require(make_version_commit(state) == state["sha"], "Cannot recreate the saved version commit")
    else:
        selector = release["tag_name"]
        require(selector in SELECTORS, "New releases must use the fix, minor or major tag")
        require(not git("status", "--porcelain"), "Release preparation requires a clean checkout")
        git("checkout", "--detach", "origin/" + default)
        previous = properties(ROOT / "gradle.properties")["mod.version"]
        version = next_version(previous, selector)
        tagged = [tag[1:] for tag in git("tag", "--list", "v*").splitlines() if VERSION.fullmatch(tag[1:])]
        require(all(version_key(version) > version_key(tag) for tag in tagged), "Calculated version must exceed existing version tags")
        state = {"repository": repo(), "release_id": release["id"], "selector": selector,
                 "branch": default, "source_sha": git("rev-parse", "HEAD"), "previous": previous,
                 "version": version, "tag": "v" + version, "timestamp": release["published_at"],
                 "name": release["name"], "body": release["body"], "prerelease": release["prerelease"]}
        state["sha"] = make_version_commit(state)
        # Persist the chosen version and commit BEFORE updating any remote refs.
        upload_asset(release["id"], "release-state.json", json.dumps(state, indent=2).encode())
    push_version_commit(state)
    write_json(ROOT / "build/release/context.json", state)
    output(sha=state["sha"], tag=state["tag"])
    summary(f"Prepared {state['tag']} from `{state['source_sha']}`. "
            f"The version commit `{state['sha']}` is on `{default}`. Title: {state['name']}")


def selectors(args=None):
    default = github("")["default_branch"]
    head = github("git/ref/heads/" + default)["object"]["sha"]
    for selector in SELECTORS:
        # A pending release owns its selector until staging retags it. Do not
        # move its ref underneath the release or take it over on a main push.
        if github("releases/tags/" + selector, missing_ok=True):
            continue
        ref = github("git/ref/tags/" + selector, missing_ok=True)
        if ref:
            if ref["object"]["sha"] != head:
                github("git/refs/tags/" + selector, method="PATCH", payload={"sha": head, "force": True})
        else:
            github("git/refs", method="POST", payload={"ref": "refs/tags/" + selector, "sha": head})
    summary("Release selectors are ready: fix, minor, major.")


def finalize_tag(state):
    release = github(f"releases/{state['release_id']}")
    check_release(release, state)
    current = assets(state["release_id"])
    require(json.loads(asset_bytes(current["release-state.json"])) == state, "Release state differs from saved preparation")
    ref = github("git/ref/tags/" + state["tag"], missing_ok=True)
    if ref:
        require(ref["object"]["type"] == "commit" and ref["object"]["sha"] == state["sha"],
                "Existing version tag points to different code")
    else:
        github("git/refs", method="POST", payload={"ref": "refs/tags/" + state["tag"], "sha": state["sha"]})
    if release["tag_name"] != state["tag"]:
        github(f"releases/{state['release_id']}", method="PATCH",
               payload={"tag_name": state["tag"], "target_commitish": state["sha"]})
    selectors()


def check_jar(path, version, loader):
    with zipfile.ZipFile(path) as archive:
        if loader == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            require(metadata["id"] == "xanders-sodium-options" and metadata["version"] == version,
                    f"Incorrect Fabric metadata in {path.name}")
            require("META-INF/neoforge.mods.toml" not in archive.namelist(), "Wrong loader metadata")
            game_range = metadata["depends"]["minecraft"]
            require(set(metadata["depends"]) <= {"fabricloader", "java", "minecraft", "sodium", "yet_another_config_lib_v3", "fabric-api"},
                    "Unexpected required mod in Fabric metadata; review publication dependencies")
        else:
            metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
            require(any(mod["modId"] == "xanders_sodium_options" and mod["version"] == version
                        for mod in metadata["mods"]), f"Incorrect NeoForge metadata in {path.name}")
            require("fabric.mod.json" not in archive.namelist(), "Wrong loader metadata")
            dependencies = metadata["dependencies"]["xanders_sodium_options"]
            game_range = next(d["versionRange"] for d in dependencies if d["modId"] == "minecraft")
            require({d["modId"] for d in dependencies if d.get("type") == "required"} <=
                    {"neoforge", "minecraft", "sodium", "yet_another_config_lib_v3"},
                    "Unexpected required mod in NeoForge metadata; review publication dependencies")
        bundled_yacl = [name for name in archive.namelist()
                        if "yet-another-config-lib" in name and name.endswith(".jar")]
        require(len(bundled_yacl) == 1, "Expected one bundled YACL; no separate download is advertised")
        needs_fabric_api = False
        if loader == "fabric":
            require("sodium" in metadata["depends"], "Published Sodium requirement is missing from the JAR")
            with zipfile.ZipFile(io.BytesIO(archive.read(bundled_yacl[0]))) as yacl:
                yacl_dependencies = json.loads(yacl.read("fabric.mod.json"))["depends"]
            needs_fabric_api = "fabric-api" in metadata["depends"] or "fabric-api" in yacl_dependencies
        else:
            require(any(d["modId"] == "sodium" and d.get("type") == "required" for d in dependencies),
                    "Published Sodium requirement is missing from the JAR")
        return game_range, needs_fabric_api


def game_supported(game, constraint):
    def key(value):
        require(re.fullmatch(r"\d+(?:\.\d+)+", value), "Unsupported Minecraft version constraint")
        parts = tuple(map(int, value.split(".")))
        return parts + (0,) * (4 - len(parts))

    if constraint.startswith("[") and constraint.endswith("]"):
        bounds = constraint[1:-1].split(",")
        return key(bounds[0]) <= key(game) <= key(bounds[-1])
    for part in constraint.split():
        match = re.fullmatch(r"(>=|<=|=)?([\d.]+)", part)
        require(match, "Unsupported Minecraft version constraint")
        operator, value = match.groups()
        if not {">=": key(game) >= key(value), "<=": key(game) <= key(value),
                "=": key(game) == key(value), None: key(game) == key(value)}[operator]:
            return False
    return bool(constraint)


def make_plan(root, ctx):
    version = validate_version(ctx["tag"], root)
    require(isinstance(ctx.get("name"), str) and ctx["name"].strip(), "Enter a release title")
    channel = "beta" if ctx["prerelease"] else "release"
    artifacts = json.loads((root / "build/libs/release-artifacts.json").read_text())
    catalog = json.loads((root / "build/release/minecraft-versions.json").read_text())
    require(artifacts and len({a["target"] for a in artifacts}) == len(artifacts), "Missing or duplicate build targets")
    targets = []
    for artifact in artifacts:
        target, minecraft, loader = (artifact[k] for k in ("target", "minecraft", "loader"))
        require(loader in ("fabric", "neoforge") and target == f"{minecraft}-{loader}", "Invalid build target")
        full_version = f"{version}+{minecraft}-{loader}"
        require(artifact["version"] == full_version, "Gradle artifact version disagrees with release")
        path = root / artifact["path"]
        require(path.resolve().is_relative_to((root / "build/libs").resolve()), "Artifact must be inside build/libs")
        require(path.is_file(), f"Missing release artifact: {path}")
        filename = path.name
        game_range, needs_fabric_api = check_jar(path, full_version, loader)
        games = sorted({game for game in catalog if game_supported(game, game_range)},
                       key=lambda v: tuple(map(int, v.split("."))))
        require(games and minecraft in games, f"No matching Minecraft releases for {target}'s compatibility declaration")
        notes = changelog_for(ctx["body"], loader)
        dependencies = [*PUBLISH_RELATIONS]
        if needs_fabric_api:
            dependencies.append(FABRIC_API_RELATION)
        label = "Fabric" if loader == "fabric" else "NeoForge"
        display = ctx["name"]
        targets.append({"target": target, "filename": filename, "path": str(path.relative_to(root)),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "sha512": hashlib.sha512(path.read_bytes()).hexdigest(),
            "modrinth": {"project_id": MODRINTH_PROJECT, "name": display,
                "version_number": full_version, "version_type": channel, "changelog": notes,
                "game_versions": games, "loaders": [loader], "environment": "client_only", "featured": False,
                "dependencies": [{"project_id": d["modrinth"], "dependency_type": d["type"]} for d in dependencies],
                "file_parts": ["file"], "primary_file": "file"},
            "curseforge": {"project_id": CURSEFORGE_PROJECT, "metadata": {
                "displayName": display, "changelog": notes, "changelogType": "markdown",
                "releaseType": channel, "gameVersionNames": [*games, label, "Client"],
                "relations": {"projects": [{"slug": d["curseforge"], "type": {"required": "requiredDependency", "embedded": "embeddedLibrary", "incompatible": "incompatible"}[d["type"]]}
                                           for d in dependencies]}}}})
    return {**ctx, "targets": targets}


def summary(text):
    print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as handle:
            handle.write(text + "\n")


def plan(args):
    ctx = json.loads(Path(args.context).read_text())
    value = make_plan(ROOT, ctx)
    write_json(ROOT / "build/release/plan.json", value)
    output(matrix=json.dumps({"target": [t["target"] for t in value["targets"]], "platform": ["modrinth", "curseforge"]}))
    summary("| Target | Minecraft versions | File |\n|---|---|---|\n" + "\n".join(
        f"| {t['target']} | {', '.join(t['modrinth']['game_versions'])} | {t['filename']} |" for t in value["targets"]))
    for loader in ("fabric", "neoforge"):
        summary(f"\n### {loader.title()} changelog\n\n{changelog_for(ctx['body'], loader)}\n")


def jar_content_hash(data):
    """Compare rebuilt archives ignoring ZIP timestamps, but not their contents."""
    digest = hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            digest.update(name.encode() + b"\0")
            digest.update(hashlib.sha256(archive.read(name)).digest())
    return digest.hexdigest()


def stage(args):
    path = ROOT / "build/release/plan.json"
    value = json.loads(path.read_text())
    require(value["repository"] == repo(), "Plan belongs to a different repository")
    finalize_tag({k: v for k, v in value.items() if k != "targets"})
    existing = assets(value["release_id"])
    for target in value["targets"]:
        file = ROOT / target["path"]
        data = file.read_bytes()
        require(hashlib.sha256(data).hexdigest() == target["sha256"], "Artifact changed after validation")
        if target["filename"] in existing:
            frozen = asset_bytes(existing[target["filename"]])
            require(jar_content_hash(data) == jar_content_hash(frozen), "Existing release JAR has different contents; refusing replacement")
            file.write_bytes(frozen)
            target["sha256"] = hashlib.sha256(frozen).hexdigest()
            target["sha512"] = hashlib.sha512(frozen).hexdigest()
        else:
            upload_asset(value["release_id"], target["filename"], data, "application/java-archive")
    if "publishing-plan.json" in existing:
        frozen_plan = json.loads(asset_bytes(existing["publishing-plan.json"]))
        require(frozen_plan == value, "Release metadata changed after staging; use a new release instead of changing uploads in progress")
    else:
        upload_asset(value["release_id"], "publishing-plan.json", json.dumps(value, indent=2).encode())
    write_json(path, value)


def multipart(field, metadata, filename, data):
    boundary = "xso-" + uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\nContent-Type: application/json\r\n\r\n'.encode(),
             json.dumps(metadata).encode(),
             f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: application/java-archive\r\n\r\n'.encode(),
             data, f"\r\n--{boundary}--\r\n".encode()]
    return b"".join(parts), {"Content-Type": "multipart/form-data; boundary=" + boundary}


def fingerprint(target, platform):
    return hashlib.sha256(json.dumps({"sha256": target["sha256"], "filename": target["filename"],
                                     "metadata": target[platform]}, sort_keys=True).encode()).hexdigest()


def find_modrinth_version(target):
    metadata = target["modrinth"]
    versions = request(f"{MR_API}/project/{metadata['project_id']}/version")
    matches = [v for v in versions if v["version_number"] == metadata["version_number"]]
    if not matches:
        return None
    require(len(matches) == 1, "Multiple Modrinth versions use this version number")
    version = matches[0]
    require(any(f["filename"] == target["filename"] and f["hashes"].get("sha512") == target["sha512"]
                for f in version["files"]), "Existing Modrinth version has different files")
    for field in ("loaders", "game_versions"):
        require(sorted(version[field]) == sorted(metadata[field]), f"Existing Modrinth {field} differs")
    for field in ("name", "environment", "changelog", "version_type"):
        require(version[field] == metadata[field], f"Existing Modrinth {field} differs")
    require(sorted((d.get("project_id"), d["dependency_type"]) for d in version["dependencies"]) ==
            sorted((d["project_id"], d["dependency_type"]) for d in metadata["dependencies"]), "Existing Modrinth dependencies differ")
    return version["id"]


def publish(args):
    value = json.loads((ROOT / "build/release/plan.json").read_text())
    require(value["repository"] == repo(), "Plan belongs to a different repository")
    release = github(f"releases/{value['release_id']}")
    check_release(release, value)
    require(release["tag_name"] == value["tag"], "Release must use the final version tag before uploading")
    target = next(t for t in value["targets"] if t["target"] == args.target)
    platform = args.platform
    token = os.environ.get(platform.upper() + "_TOKEN", "")
    require(token, f"Missing {platform.upper()}_TOKEN in the publishing environment")
    current = assets(value["release_id"])
    frozen = json.loads(asset_bytes(current["publishing-plan.json"]))
    require(frozen == value, "Plan does not match the frozen GitHub release plan")
    receipt_name = f"publish-{platform}-{args.target}.json"
    pending_name = f"publish-{platform}-{args.target}.pending.json"
    key = fingerprint(target, platform)
    if receipt_name in current:
        receipt = json.loads(asset_bytes(current[receipt_name]))
        require(receipt["fingerprint"] == key, "Previous upload used different bytes or metadata")
        summary(f"{platform} / {args.target}: already uploaded ({receipt['id']})")
        return
    recovered = find_modrinth_version(target) if platform == "modrinth" else None
    if recovered:
        upload_asset(value["release_id"], receipt_name, json.dumps({"fingerprint": key, "id": recovered}).encode())
        summary(f"{platform} / {args.target}: recovered existing upload ({recovered})")
        return
    require(pending_name not in current, f"Unconfirmed {platform} upload for {args.target}. Inspect the platform for an existing upload before retrying")
    data = asset_bytes(current[target["filename"]])
    require(hashlib.sha256(data).hexdigest() == target["sha256"], "Release asset checksum mismatch")
    if platform == "curseforge":
        # Validate names and token with a read before marking an upload pending.
        versions = request(CF_API + "/game/versions", headers={"X-Api-Token": token})
        names = {v["name"] for v in versions}
        require(set(target[platform]["metadata"]["gameVersionNames"]) <= names,
                "CurseForge does not recognize every game version, loader or environment in this upload")
    pending = upload_asset(value["release_id"], pending_name, json.dumps({"fingerprint": key}).encode())
    try:
        if platform == "modrinth":
            body, headers = multipart("data", target[platform], target["filename"], data)
            result = request(MR_API + "/version", "POST", body, {**headers, "Authorization": token})
        else:
            body, headers = multipart("metadata", target[platform]["metadata"], target["filename"], data)
            result = request(f"{CF_API}/projects/{target[platform]['project_id']}/upload-file", "POST", body,
                             {**headers, "X-Api-Token": token})
    except ApiError as error:
        # These statuses confirm rejection. Timeouts/5xx retain the pending marker.
        if error.status in (400, 401, 403, 404, 422, 429):
            github(f"releases/assets/{pending['id']}", method="DELETE")
        raise
    require(result and result.get("id"), "Upload response has no ID; inspect the pending upload")
    upload_asset(value["release_id"], receipt_name, json.dumps({"fingerprint": key, "id": str(result["id"])}).encode())
    summary(f"{platform} / {args.target}: uploaded ({result['id']})")


def recover_curseforge(args):
    """Record a manually confirmed upload after verifying its public file bytes."""
    value = json.loads((ROOT / "build/release/plan.json").read_text())
    require(value["repository"] == repo(), "Plan belongs to a different repository")
    current = assets(value["release_id"])
    require(json.loads(asset_bytes(current["publishing-plan.json"])) == value, "Recovery plan differs from frozen release plan")
    target = next(t for t in value["targets"] if t["target"] == args.target)
    require(args.file_id > 0, "CurseForge file ID must be positive")
    data = request(f"https://www.curseforge.com/api/v1/mods/{target['curseforge']['project_id']}/files/{args.file_id}/download", raw=True)
    require(hashlib.sha256(data).hexdigest() == target["sha256"], "CurseForge file does not match the release JAR")
    name = f"publish-curseforge-{args.target}.json"
    require(name not in current, "This upload already has a receipt")
    upload_asset(value["release_id"], name, json.dumps({"fingerprint": fingerprint(target, "curseforge"), "id": str(args.file_id)}).encode())
    summary(f"Recorded verified CurseForge upload {args.file_id}; the publishing workflow can now be rerun.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    subs.add_parser("selectors")
    p = subs.add_parser("prepare")
    p.add_argument("--release-id", type=int, required=True)
    p = subs.add_parser("plan")
    p.add_argument("--context", default="build/release/context.json")
    subs.add_parser("stage")
    p = subs.add_parser("publish")
    p.add_argument("--target", required=True)
    p.add_argument("--platform", choices=["modrinth", "curseforge"], required=True)
    p = subs.add_parser("recover-curseforge")
    p.add_argument("--target", required=True)
    p.add_argument("--file-id", type=int, required=True)
    subs.add_parser("minecraft-versions")
    args = parser.parse_args()
    {"selectors": selectors, "prepare": prepare, "minecraft-versions": minecraft_versions,
     "plan": plan, "stage": stage, "publish": publish, "recover-curseforge": recover_curseforge}[args.command](args)


if __name__ == "__main__":
    try:
        main()
    except (ReleaseError, subprocess.CalledProcessError, KeyError, StopIteration, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        sys.exit(1)
