import copy
from contextlib import ExitStack
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import subprocess
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release.py")
r = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(r)


class GitHubRequestTests(unittest.TestCase):
    def test_repository_lookup_uses_exact_endpoint_without_trailing_slash(self):
        with patch.dict(r.os.environ, {"GITHUB_REPOSITORY": "owner/repo", "GH_TOKEN": "test-only"}), \
                patch.object(r, "request", return_value={"default_branch": "main"}) as request:
            self.assertEqual(r.github(""), {"default_branch": "main"})
            request.assert_called_once_with("https://api.github.com/repos/owner/repo", headers=r.gh_headers())

    def test_subresource_paths_and_request_arguments_are_preserved(self):
        with patch.dict(r.os.environ, {"GITHUB_REPOSITORY": "owner/repo", "GH_TOKEN": "test-only"}), \
                patch.object(r, "request") as request:
            r.github("git/ref/tags/fix", missing_ok=True)
            request.assert_called_with("https://api.github.com/repos/owner/repo/git/ref/tags/fix",
                                       headers=r.gh_headers(), missing_ok=True)
            payload = {"ref": "refs/tags/fix", "sha": "a" * 40}
            r.github("git/refs", method="POST", payload=payload)
            request.assert_called_with("https://api.github.com/repos/owner/repo/git/refs",
                                       headers=r.gh_headers(), method="POST", payload=payload)


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.enterContext(patch.object(r, "ROOT", self.root))
        self.enterContext(patch.dict(r.os.environ, {"GITHUB_REPOSITORY": "owner/repo",
                          "GITHUB_EVENT_NAME": "release", "GITHUB_RUN_ATTEMPT": "1"}))
        self.release = {"id": 123, "name": "Release", "body": "Fixed it", "draft": False,
                        "prerelease": False, "tag_name": "fix", "published_at": r.datetime.now(r.timezone.utc).isoformat()}
        self.args = SimpleNamespace(release_id=123, target=None, platform=None)

    def test_initial_published_event_can_start_without_state(self):
        with patch.object(r, "github", return_value=self.release), patch.object(r, "recovery_artifacts", return_value=[]):
            r.restore(self.args)
        self.assertFalse((self.root / "build/release/context.json").exists())

    def test_missing_state_never_silently_restarts_retry(self):
        for event, attempt, tag in [("workflow_dispatch", "1", "fix"), ("release", "2", "fix"), ("release", "1", "v3.5.2")]:
            with self.subTest(event=event, attempt=attempt, tag=tag), \
                    patch.dict(r.os.environ, {"GITHUB_EVENT_NAME": event, "GITHUB_RUN_ATTEMPT": attempt}), \
                    patch.object(r, "github", return_value={**self.release, "tag_name": tag}), \
                    patch.object(r, "recovery_artifacts", return_value=[]), self.assertRaisesRegex(r.ReleaseError, "missing"):
                r.restore(self.args)

    def test_expired_recovery_window_stops_before_looking_up_uploads(self):
        self.release["published_at"] = (r.datetime.now(r.timezone.utc) - r.timedelta(days=15)).isoformat()
        with patch.object(r, "github", return_value=self.release), patch.object(r, "recovery_artifacts") as lookup:
            with self.assertRaisesRegex(r.ReleaseError, "expired"):
                r.restore(self.args)
        lookup.assert_not_called()

    def test_untrusted_workflow_artifacts_are_ignored_and_pagination_is_followed(self):
        def api(path):
            if path.endswith("page=1"):
                return {"artifacts": [{"name": "build"}] * 99 + [self.artifact(1, 1)]}
            if path.endswith("page=2"):
                return {"artifacts": [self.artifact(2, 2), self.artifact(3, 3), self.artifact(4, 4)]}
            run_id = int(path.rsplit("/", 1)[1])
            return {"path": ".github/workflows/publish-release.yml", "event": "pull_request" if run_id == 1 else "release",
                    "head_repository": {"full_name": "fork/repo" if run_id == 3 else "owner/repo"}}
        with patch.object(r, "github", side_effect=api):
            self.assertEqual([a["id"] for a in r.recovery_artifacts(123)], [4, 2])

    def artifact(self, artifact_id, run_id, kind="context"):
        return {"id": artifact_id, "name": f"release-123-{kind}-1", "expired": False,
                "workflow_run": {"id": run_id}, "created_at": "2026-09-22T12:00:00Z"}

    def test_expired_artifact_stops_recovery(self):
        artifact = {**self.artifact(1, 1), "expired": True}
        with patch.object(r, "github", side_effect=[{"artifacts": [artifact]}, {
            "path": ".github/workflows/publish-release.yml", "event": "release",
            "head_repository": {"full_name": "owner/repo"}}]), self.assertRaisesRegex(r.ReleaseError, "expired"):
            r.recovery_artifacts(123)

    def test_artifact_download_reads_only_the_expected_json(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr("context.json", '{"version": "3.5.2"}')
        with patch.object(r, "github", return_value=data.getvalue()):
            self.assertEqual(r.artifact_json(1, "context.json"), {"version": "3.5.2"})
            with self.assertRaisesRegex(r.ReleaseError, "Unexpected"):
                r.artifact_json(1, "plan.json")

    def test_public_upload_rejects_json(self):
        with patch.object(r, "request") as request, self.assertRaisesRegex(r.ReleaseError, "Only JARs"):
            r.upload_asset(123, "release-state.json", b"{}")
        request.assert_not_called()


class VersionTests(unittest.TestCase):
    def test_three_release_choices(self):
        self.assertEqual(r.next_version("3.5.1", "fix"), "3.5.2")
        self.assertEqual(r.next_version("3.5.1", "minor"), "3.6.0")
        self.assertEqual(r.next_version("3.5.1", "major"), "4.0.0")

    def test_rejects_invalid_versions_or_choices(self):
        for value in ["03.6.0", "3.6", "3.6.0-dev", "3.6.0\nINJECTED=1", "v3.6.0"]:
            with self.subTest(value=value), self.assertRaises(r.ReleaseError):
                r.next_version(value, "fix")
        for choice in ["patch", "exact", "unknown"]:
            with self.subTest(choice=choice), self.assertRaises(r.ReleaseError):
                r.next_version("3.5.1", choice)


class NotesTests(unittest.TestCase):
    def test_plain_notes_are_shared(self):
        text = "## Fixes\nFixed launch.\n\n## Other changes\nOther text."
        for loader in ["fabric", "neoforge"]:
            self.assertEqual(r.changelog_for(text, loader), text)

    def test_additions_and_replacement(self):
        text = "## Shared\nCommon.\n## Fabric\nFabric only.\n## NeoForge\nNeo only."
        self.assertEqual(r.changelog_for(text, "fabric"), "Common.\n\nFabric only.")
        self.assertEqual(r.changelog_for(text, "neoforge"), "Common.\n\nNeo only.")
        self.assertEqual(r.changelog_for(text + "\n## NeoForge override\nEntirely different.", "neoforge"), "Entirely different.")

    def test_fenced_headings_are_not_sections(self):
        text = "Example:\n```md\n## NeoForge\nexample text\n```"
        self.assertEqual(r.changelog_for(text, "fabric"), text)

    def test_empty_or_duplicate_sections_fail(self):
        for text in ["", "<!-- Write notes -->", "## Fabric\nOnly Fabric", "## Shared\nOne\n## Shared\nTwo"]:
            with self.subTest(text=text), self.assertRaises(r.ReleaseError):
                r.changelog_for(text, "neoforge")


class ArtifactFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "gradle.properties").write_text("mod.version=3.5.2\n")
        r.write_json(self.root / "build/release/minecraft-versions.json",
                     ["1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.1.3", "26.2", "26.3", "26.4"])
        self.ctx = {"tag": "v3.5.2", "sha": "a" * 40, "release_id": 1,
                    "source_sha": "b" * 40, "version": "3.5.2", "previous": "3.5.1", "selector": "fix",
                    "branch": "main", "timestamp": r.datetime.now(r.timezone.utc).isoformat(),
                    "name": "Xander's Sodium Options: Continued 3.5.2", "repository": "owner/repo", "body": "## Shared\nFixed launch.\n## NeoForge\nFixed detection.", "prerelease": False}
        self.artifacts = []
        for folder in sorted((r.ROOT / "versions").iterdir()):
            props_file = folder / "gradle.properties"
            if not props_file.is_file():
                continue
            props = r.properties(props_file)
            constraint = (props.get("mod.target.range", "[" + props["mod.target"] + "]")
                          if folder.name.endswith("neoforge") else props["mod.target"])
            self.fixture_jar(folder.name, constraint, folder.name == "1.21.11-fabric")

    def fixture_jar(self, target, constraint, needs_api=False):
        game, loader = target.rsplit("-", 1)
        full = f"3.5.2+{game}-{loader}"
        path = self.root / "build/libs" / loader / f"xanders-sodium-options-{full}.jar"
        path.parent.mkdir(parents=True, exist_ok=True)
        yacl = io.BytesIO()
        with zipfile.ZipFile(yacl, "w") as z:
            z.writestr("fabric.mod.json", json.dumps({"depends": {
                "fabric-api" if needs_api else "fabric-resource-loader-v1": "*"}}))
        with zipfile.ZipFile(path, "w") as z:
            if loader == "fabric":
                z.writestr("fabric.mod.json", json.dumps({"id": "xanders-sodium-options", "version": full,
                    "depends": {"minecraft": constraint, "sodium": ">=0.8.4", "yet_another_config_lib_v3": ">=3.8.2"}}))
            else:
                z.writestr("META-INF/neoforge.mods.toml", f'[[mods]]\nmodId="xanders_sodium_options"\nversion="{full}"\n'
                    f'[[dependencies.xanders_sodium_options]]\nmodId="minecraft"\ntype="required"\nversionRange="{constraint}"\n'
                    '[[dependencies.xanders_sodium_options]]\nmodId="sodium"\ntype="required"\n')
            z.writestr("META-INF/jars/yet-another-config-lib.jar", yacl.getvalue())
        if not any(a["target"] == target for a in self.artifacts):
            self.artifacts.append({"target": target, "minecraft": game, "loader": loader,
                "version": full, "path": str(path.relative_to(self.root))})
        r.write_json(self.root / "build/libs/release-artifacts.json", self.artifacts)


class PlanTests(ArtifactFixture):
    def test_all_targets_and_exact_dependency_metadata(self):
        with patch.object(r, "request", side_effect=AssertionError("Plan must be offline")):
            plan = r.make_plan(self.root, self.ctx)
        self.assertEqual(len(plan["targets"]), len(self.artifacts))
        for t in plan["targets"]:
            mr = t["modrinth"]
            cf = t["curseforge"]
            self.assertEqual(mr["project_id"], "fGB38jk4")
            self.assertEqual(cf["project_id"], 1454606)
            deps = {d["project_id"]: d["dependency_type"] for d in mr["dependencies"]}
            self.assertEqual(deps["AANobbMI"], "required")
            self.assertEqual(deps["Bh37bMuy"], "incompatible")
            self.assertEqual("P7dR8mSH" in deps, t["target"] == "1.21.11-fabric")
            self.assertEqual(len(deps), 3 if t["target"] == "1.21.11-fabric" else 2)
            self.assertEqual(mr["name"], "Xander's Sodium Options: Continued 3.5.2")
            self.assertEqual(mr["environment"], "client_only")
            self.assertEqual(cf["metadata"]["displayName"], mr["name"])
            relations = {d["slug"]: d["type"] for d in cf["metadata"]["relations"]["projects"]}
            expected = {"sodium": "requiredDependency", "reeses-sodium-options": "incompatible"}
            if t["target"] == "1.21.11-fabric":
                expected["fabric-api"] = "requiredDependency"
            self.assertEqual(relations, expected)
            self.assertEqual("Fixed detection." in mr["changelog"], t["target"].endswith("neoforge"))
            if t["target"].startswith("26.1-"):
                self.assertEqual(mr["game_versions"], ["26.1", "26.1.1", "26.1.2"])

    def test_tag_must_match_version(self):
        with self.assertRaises(r.ReleaseError):
            r.make_plan(self.root, {**self.ctx, "tag": "v3.5.3"})

    def test_retry_uses_saved_version_even_when_tooling_checkout_has_advanced(self):
        (self.root / "gradle.properties").write_text("mod.version=3.6.0\n")
        plan = r.make_plan(self.root, self.ctx)
        self.assertTrue(all(t["modrinth"]["version_number"].startswith("3.5.2+") for t in plan["targets"]))

    def test_prerelease_checkbox_controls_platform_channel(self):
        for t in r.make_plan(self.root, {**self.ctx, "prerelease": True})["targets"]:
            self.assertEqual(t["modrinth"]["version_type"], "beta")
            self.assertEqual(t["curseforge"]["metadata"]["releaseType"], "beta")

    def test_title_is_copied_literally_including_placeholder_text(self):
        title = "A custom release {version}"
        for t in r.make_plan(self.root, {**self.ctx, "name": title})["targets"]:
            self.assertEqual(t["modrinth"]["name"], title)
            self.assertEqual(t["curseforge"]["metadata"]["displayName"], title)
        with self.assertRaises(r.ReleaseError):
            r.make_plan(self.root, {**self.ctx, "name": ""})

    def test_missing_artifact_prevents_plan(self):
        next((self.root / "build/libs").rglob("*.jar")).unlink()
        with self.assertRaises(r.ReleaseError):
            r.make_plan(self.root, self.ctx)

    def test_new_gradle_artifact_is_automatically_included(self):
        self.fixture_jar("26.4-fabric", "26.4")
        targets = r.make_plan(self.root, self.ctx)["targets"]
        self.assertEqual(len(targets), len(self.artifacts))
        self.assertEqual(targets[-1]["modrinth"]["game_versions"], ["26.4"])

    def test_unregistered_leftover_jar_is_ignored(self):
        (self.root / "build/libs/fabric/old-build.jar").write_bytes(b"unused")
        self.assertEqual(len(r.make_plan(self.root, self.ctx)["targets"]), len(self.artifacts))

    def test_jar_compatibility_controls_published_versions(self):
        self.fixture_jar("26.1-fabric", ">=26.1 <=26.1.1")
        target = next(t for t in r.make_plan(self.root, self.ctx)["targets"] if t["target"] == "26.1-fabric")
        self.assertEqual(target["modrinth"]["game_versions"], ["26.1", "26.1.1"])

    def test_catalog_must_contain_the_target_version(self):
        r.write_json(self.root / "build/release/minecraft-versions.json", ["1.21.11"])
        with self.assertRaises(r.ReleaseError):
            r.make_plan(self.root, self.ctx)

    def test_range_expansion_does_not_invent_game_versions(self):
        path = self.root / "build/release/minecraft-versions.json"
        catalog = json.loads(path.read_text())
        catalog.remove("26.1.1")
        r.write_json(path, catalog)
        target = next(t for t in r.make_plan(self.root, self.ctx)["targets"] if t["target"] == "26.1-fabric")
        self.assertEqual(target["modrinth"]["game_versions"], ["26.1", "26.1.2"])

    def test_fabric_api_relationship_tracks_bundled_yacl_requirement(self):
        self.fixture_jar("26.3-fabric", "26.3", needs_api=True)
        target = next(t for t in r.make_plan(self.root, self.ctx)["targets"] if t["target"] == "26.3-fabric")
        self.assertIn(r.FABRIC_API_RELATION["modrinth"], [d["project_id"] for d in target["modrinth"]["dependencies"]])
        self.fixture_jar("1.21.11-fabric", "=1.21.11", needs_api=False)
        target = next(t for t in r.make_plan(self.root, self.ctx)["targets"] if t["target"] == "1.21.11-fabric")
        self.assertEqual(len(target["modrinth"]["dependencies"]), 2)

    def test_wrong_loader_or_version_fails(self):
        path = next((self.root / "build/libs/fabric").glob("*.jar"))
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("fabric.mod.json", json.dumps({"id": "xanders-sodium-options", "version": "wrong"}))
        with self.assertRaises(r.ReleaseError):
            r.make_plan(self.root, self.ctx)

    def test_optional_mod_cannot_become_required_unnoticed(self):
        path = next((self.root / "build/libs/fabric").glob("*.jar"))
        with zipfile.ZipFile(path) as z:
            metadata = json.loads(z.read("fabric.mod.json"))
        metadata["depends"]["lambdynlights"] = "*"
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("fabric.mod.json", json.dumps(metadata))
        with self.assertRaises(r.ReleaseError):
            r.make_plan(self.root, self.ctx)

    def test_modrinth_existing_upload_requires_matching_bytes_and_metadata(self):
        target = r.make_plan(self.root, self.ctx)["targets"][0]
        published = {**target["modrinth"], "id": "existing", "files": [
            {"filename": target["filename"], "hashes": {"sha512": target["sha512"]}}]}
        with patch.object(r, "request", return_value=[published]):
            self.assertEqual(r.find_modrinth_version(target), "existing")
        for key, value in [("changelog", "changed"), ("dependencies", []), ("loaders", ["neoforge"]),
                           ("name", "different"), ("environment", "client_and_server")]:
            with self.subTest(key=key), patch.object(r, "request", return_value=[{**published, key: value}]), self.assertRaises(r.ReleaseError):
                r.find_modrinth_version(target)
        published["files"][0]["hashes"]["sha512"] = "different"
        with patch.object(r, "request", return_value=[published]), self.assertRaises(r.ReleaseError):
            r.find_modrinth_version(target)

    def test_fingerprint_covers_notes_and_file(self):
        target = r.make_plan(self.root, self.ctx)["targets"][0]
        original = r.fingerprint(target, "curseforge")
        changed = copy.deepcopy(target)
        changed["curseforge"]["metadata"]["changelog"] = "new notes"
        self.assertNotEqual(original, r.fingerprint(changed, "curseforge"))
        changed = {**target, "sha256": "other"}
        self.assertNotEqual(original, r.fingerprint(changed, "curseforge"))


class PublishTests(ArtifactFixture):
    def setUp(self):
        super().setUp()
        self.plan = r.make_plan(self.root, self.ctx)
        for name, value in [("context.json", self.ctx), ("plan.json", self.plan), ("frozen-plan.json", self.plan)]:
            r.write_json(self.root / "build/release" / name, value)
        self.target = self.plan["targets"][0]
        self.remote = {t["filename"]: (self.root / t["path"]).read_bytes() for t in self.plan["targets"]}
        self.enterContext(patch.object(r, "ROOT", self.root))
        self.enterContext(patch.dict(r.os.environ, {"GITHUB_REPOSITORY": "owner/repo",
            "MODRINTH_TOKEN": "test-only", "CURSEFORGE_TOKEN": "test-only"}))
        self.enterContext(patch.object(r, "assets", side_effect=lambda _: {n: {"name": n} for n in self.remote}))
        self.enterContext(patch.object(r, "asset_bytes", side_effect=lambda a: self.remote[a["name"]]))
        self.upload = self.enterContext(patch.object(r, "upload_asset", side_effect=self.save_asset))
        self.enterContext(patch.object(r, "github", return_value={**self.ctx, "id": 1, "draft": False,
                          "tag_name": "v3.5.2", "published_at": self.ctx["timestamp"]}))
        self.enterContext(patch.object(r, "finalize_tag"))
        self.lookup = self.enterContext(patch.object(r, "find_modrinth_version", return_value=None))
        self.request = self.enterContext(patch.object(r, "request", side_effect=self.respond))
        self.enterContext(patch.object(r, "summary"))
        self.output = self.enterContext(patch.object(r, "output"))
        self.response = {"id": 12345}
        self.checkpoint = self.enterContext(patch.object(r, "artifact_json", side_effect=lambda _, name:
            json.loads((self.root / "build/release" / name).read_text())))

    def save_asset(self, release_id, name, data, *args):
        self.assertTrue(name.endswith(".jar"))
        self.assertNotIn(name, self.remote)
        self.remote[name] = data

    def respond(self, url, *args, **kwargs):
        if url.endswith("/game/versions"):
            return [{"name": name} for name in self.target["curseforge"]["metadata"]["gameVersionNames"]]
        if isinstance(self.response, Exception):
            raise self.response
        return self.response

    def args(self, platform):
        return SimpleNamespace(target=self.target["target"], platform=platform, artifact_id=123)

    def restore_file(self, name):
        value = json.loads((self.root / "build/release" / name).read_text())
        r.write_json(self.root / "build/release/upload-state.json", value)

    def test_staging_and_publishing_attach_only_jars(self):
        self.remote.clear()
        r.stage(None)
        self.assertEqual(set(self.remote), {t["filename"] for t in self.plan["targets"]})
        self.upload.reset_mock()
        for platform in ["modrinth", "curseforge"]:
            r.begin_upload(self.args(platform))
            r.publish(self.args(platform))
        self.upload.assert_not_called()

    def test_successful_upload_is_skipped_after_runner_restart(self):
        r.begin_upload(self.args("curseforge"))
        r.publish(self.args("curseforge"))
        self.restore_file("outcome.json")
        self.request.reset_mock()
        r.begin_upload(self.args("curseforge"))
        self.output.assert_called_with(upload="false")
        self.request.assert_not_called()

    def test_unconfirmed_upload_blocks_a_second_post(self):
        for platform in ["modrinth", "curseforge"]:
            with self.subTest(platform=platform):
                (self.root / "build/release/upload-state.json").unlink(missing_ok=True)
                r.begin_upload(self.args(platform))
                self.response = r.ReleaseError("No confirmed response")
                with self.assertRaises(r.ReleaseError):
                    r.publish(self.args(platform))
                self.restore_file("pending.json")
                self.request.reset_mock()
                with self.assertRaisesRegex(r.ReleaseError, "Unconfirmed"):
                    r.begin_upload(self.args(platform))
                self.request.assert_not_called()

    def test_confirmed_rejection_can_be_retried(self):
        r.begin_upload(self.args("modrinth"))
        self.response = r.ApiError(422, "api.modrinth.com")
        with self.assertRaises(r.ApiError):
            r.publish(self.args("modrinth"))
        self.restore_file("outcome.json")
        r.begin_upload(self.args("modrinth"))
        self.output.assert_called_with(upload="true")

    def test_modrinth_success_is_recovered_after_lost_response(self):
        r.begin_upload(self.args("modrinth"))
        self.restore_file("pending.json")
        self.lookup.return_value = "existing"
        r.begin_upload(self.args("modrinth"))
        self.output.assert_called_with(upload="false")
        self.assertEqual(json.loads((self.root / "build/release/outcome.json").read_text())["id"], "existing")
        self.request.assert_not_called()

    def test_upload_requires_a_matching_remote_checkpoint(self):
        r.begin_upload(self.args("modrinth"))
        self.checkpoint.side_effect = None
        self.checkpoint.return_value = {}
        with self.assertRaisesRegex(r.ReleaseError, "intent must be saved"):
            r.publish(self.args("modrinth"))
        self.request.assert_not_called()

    def test_changed_jar_or_receipt_prevents_upload(self):
        self.remote[self.target["filename"]] = b"changed"
        with self.assertRaisesRegex(r.ReleaseError, "checksum"):
            r.begin_upload(self.args("modrinth"))
        record = r.upload_record(self.plan, self.target, "modrinth", "complete", 123)
        record["fingerprint"] = "changed"
        r.write_json(self.root / "build/release/upload-state.json", record)
        with self.assertRaisesRegex(r.ReleaseError, "different bytes"):
            r.begin_upload(self.args("modrinth"))
        self.request.assert_not_called()

    def test_saved_notes_cannot_be_changed_on_retry(self):
        self.plan["targets"][0]["modrinth"]["changelog"] = "changed"
        r.write_json(self.root / "build/release/plan.json", self.plan)
        with self.assertRaisesRegex(r.ReleaseError, "metadata changed"):
            r.stage(None)
        self.upload.assert_not_called()

    def test_missing_plan_after_uploads_started_blocks_rebuilding_metadata(self):
        artifacts = [{"id": 2, "name": "release-1-pending-curseforge-1.21.11-fabric-1"},
                     {"id": 1, "name": "release-1-context-1"}]
        self.checkpoint.side_effect = lambda _, name: self.ctx
        with patch.object(r, "recovery_artifacts", return_value=artifacts), self.assertRaisesRegex(r.ReleaseError, "plan is missing"):
            r.restore(SimpleNamespace(release_id=1, target=None, platform=None))

    def test_restore_uses_latest_upload_checkpoint_across_runs(self):
        target, platform = self.target["target"], "curseforge"
        pending = r.upload_record(self.plan, self.target, platform, "pending")
        complete = r.upload_record(self.plan, self.target, platform, "complete", 123)
        def item(i, kind):
            return {"id": i, "name": f"release-1-{kind}-1"}
        artifacts = [item(4, f"result-{platform}-{target}"), item(3, f"pending-{platform}-{target}"),
                     item(2, "plan"), item(1, "context")]
        self.checkpoint.side_effect = lambda artifact_id, _: {1: self.ctx, 2: self.plan, 3: pending, 4: complete}[artifact_id]
        with patch.object(r, "recovery_artifacts", return_value=artifacts):
            r.restore(SimpleNamespace(release_id=1, target=target, platform=platform))
        self.assertEqual(json.loads((self.root / "build/release/upload-state.json").read_text()), complete)
        # A later pending attempt must win over an older confirmed rejection.
        artifacts.insert(0, item(5, f"pending-{platform}-{target}"))
        self.checkpoint.side_effect = lambda artifact_id, _: {1: self.ctx, 2: self.plan, 3: pending, 4: complete, 5: pending}[artifact_id]
        with patch.object(r, "recovery_artifacts", return_value=artifacts):
            r.restore(SimpleNamespace(release_id=1, target=target, platform=platform))
        self.assertEqual(json.loads((self.root / "build/release/upload-state.json").read_text()), pending)


class ReleaseFlowTests(unittest.TestCase):
    """Exercise real commits/refs against a local bare remote; all HTTP is fake."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "checkout"
        self.root.mkdir()
        self.remote = Path(self.temp.name) / "remote.git"
        self.git("init", "--bare", str(self.remote))
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        (self.root / "gradle.properties").write_text("mod.version=3.5.1\nother=unchanged\n")
        (self.root / ".gitignore").write_text("build/\n")
        (self.root / "code.txt").write_text("original\n")
        self.git("add", ".")
        self.git("commit", "-m", "Initial code")
        self.source = self.git("rev-parse", "HEAD")
        self.git("remote", "add", "origin", str(self.remote))
        self.git("push", "origin", "main")
        self.release = {"id": 1, "tag_name": "fix", "draft": False, "immutable": False,
                        "name": "My exact title", "body": "- Fixed the bug.",
                        "prerelease": False, "published_at": r.datetime.now(r.timezone.utc).isoformat()}
        self.saved = {}
        self.stack = self.enterContext(ExitStack())
        self.stack.enter_context(patch.object(r, "ROOT", self.root))
        self.stack.enter_context(patch.object(r, "git", side_effect=self.git))
        self.stack.enter_context(patch.dict(r.os.environ, {"GITHUB_REPOSITORY": "owner/repo", "GITHUB_EVENT_NAME": "release", "GITHUB_RUN_ATTEMPT": "1"}))
        self.stack.enter_context(patch.object(r, "request", side_effect=AssertionError("No live HTTP in tests")))
        self.stack.enter_context(patch.object(r, "github", side_effect=self.api))
        self.stack.enter_context(patch.object(r, "summary"))
        self.stack.enter_context(patch.object(r, "output"))
        self.stack.enter_context(patch.object(r, "upload_asset", side_effect=AssertionError("Version state must not be a public asset")))
        self.stack.enter_context(patch.object(r, "artifact_json", side_effect=lambda _, name: copy.deepcopy(self.saved[name])))

    def git(self, *args, env=None):
        env = {**r.os.environ, **(env or {}), "GIT_CONFIG_GLOBAL": r.os.devnull, "GIT_CONFIG_NOSYSTEM": "1"}
        return subprocess.check_output(["git", *args], cwd=self.root, text=True,
                                       stderr=subprocess.STDOUT, env=env).strip()

    def remote_git(self, *args):
        return self.git("--git-dir=" + str(self.remote), *args)

    def api(self, path, method="GET", payload=None, missing_ok=False):
        if path == "":
            return {"default_branch": "main"}
        if path == f"releases/{self.release['id']}":
            if method == "PATCH":
                self.release.update(payload)
            return dict(self.release)
        if path.startswith("releases/tags/"):
            return dict(self.release) if path.rsplit("/", 1)[-1] == self.release["tag_name"] else None
        if path.startswith("git/ref/"):
            ref = "refs/" + path.removeprefix("git/ref/")
            try:
                sha = self.remote_git("rev-parse", "--verify", ref)
            except subprocess.CalledProcessError:
                if missing_ok:
                    return None
                raise
            return {"object": {"sha": sha, "type": "commit"}}
        if path == "git/refs" and method == "POST":
            self.remote_git("update-ref", payload["ref"], payload["sha"], "0" * 40)
            return {}
        if path.startswith("git/refs/tags/") and method == "PATCH":
            self.assertIn(path.rsplit("/", 1)[-1], r.SELECTORS)
            self.remote_git("update-ref", path.removeprefix("git/"), payload["sha"])
            return {}
        raise AssertionError((path, method, payload))

    def prepare(self):
        r.prepare(SimpleNamespace(release_id=self.release["id"]))
        state = json.loads((self.root / "build/release/context.json").read_text())
        self.saved["context.json"] = copy.deepcopy(state)  # Simulate upload-artifact.
        r.commit_version(SimpleNamespace(artifact_id=1))
        return state

    def test_preparation_does_not_push_until_context_is_saved(self):
        r.prepare(SimpleNamespace(release_id=1))
        self.assertEqual(self.remote_git("rev-parse", "main"), self.source)
        with patch.object(r, "artifact_json", return_value={}):
            with self.assertRaisesRegex(r.ReleaseError, "must be saved"):
                r.commit_version(SimpleNamespace(artifact_id=1))
        self.assertEqual(self.remote_git("rev-parse", "main"), self.source)

    def test_release_bumps_commits_and_retags_without_changing_title_or_notes(self):
        state = self.prepare()
        self.assertEqual(state["source_sha"], self.source)
        self.assertEqual(state["version"], "3.5.2")
        self.assertEqual(self.remote_git("show", "main:gradle.properties"),
                         "mod.version=3.5.2\nother=unchanged")
        self.assertEqual(self.git("diff", "--name-only", self.source, state["sha"]), "gradle.properties")
        r.finalize_tag(state)
        self.assertEqual(self.release["tag_name"], "v3.5.2")
        self.assertEqual(self.release["name"], "My exact title")
        self.assertEqual(self.release["body"], "- Fixed the bug.")
        for tag in ["v3.5.2", *r.SELECTORS]:
            self.assertEqual(self.remote_git("rev-parse", "refs/tags/" + tag), state["sha"])
        self.assertEqual(self.prepare(), state)  # Original release ID still works after retagging.
        r.finalize_tag(state)
        self.assertEqual(self.remote_git("rev-list", "--count", "main"), "2")

    def test_saved_bump_resumes_after_interruption_before_push(self):
        with patch.object(r, "push_version_commit", side_effect=r.ReleaseError("interrupted")):
            with self.assertRaisesRegex(r.ReleaseError, "interrupted"):
                self.prepare()
        state = copy.deepcopy(self.saved["context.json"])
        self.assertEqual(self.remote_git("rev-parse", "main"), self.source)
        self.assertEqual(self.prepare(), state)
        self.assertEqual(self.remote_git("rev-parse", "main"), state["sha"])

    def test_same_selector_can_publish_the_next_release_without_changing_history(self):
        first = self.prepare()
        r.finalize_tag(first)
        self.saved = {}
        (self.root / "build/release/context.json").unlink()
        self.release.update(id=2, tag_name="fix", name="Another fix", body="- Another change.")
        second = self.prepare()
        r.finalize_tag(second)
        self.assertEqual(second["version"], "3.5.3")
        self.assertEqual(self.release["tag_name"], "v3.5.3")
        self.assertEqual(self.remote_git("rev-parse", "refs/tags/v3.5.2"), first["sha"])
        self.assertEqual(self.remote_git("rev-parse", "refs/tags/fix"), second["sha"])
        self.assertEqual(self.remote_git("show", "main:gradle.properties").splitlines()[0], "mod.version=3.5.3")

    def test_main_advancing_preserves_new_code_and_original_release_snapshot(self):
        with patch.object(r, "push_version_commit", side_effect=r.ReleaseError("interrupted")):
            with self.assertRaises(r.ReleaseError):
                self.prepare()
        self.git("checkout", "--detach", self.source)
        (self.root / "code.txt").write_text("new code merged while preparing\n")
        self.git("add", "code.txt")
        self.git("commit", "-m", "Another fix")
        self.git("push", "origin", "HEAD:main")
        state = self.prepare()
        self.assertEqual(self.remote_git("show", "main:code.txt"), "new code merged while preparing")
        self.assertEqual(self.remote_git("show", "main:gradle.properties").splitlines()[0], "mod.version=3.5.2")
        self.assertEqual(self.git("show", state["sha"] + ":code.txt"), "original")
        self.assertTrue(r.ancestor(state["sha"], self.remote_git("rev-parse", "main")))

    def test_selectors_never_take_over_pending_release(self):
        self.remote_git("update-ref", "refs/tags/fix", self.source)
        state = self.prepare()
        r.selectors()
        self.assertEqual(self.remote_git("rev-parse", "refs/tags/fix"), self.source)
        self.assertEqual(self.remote_git("rev-parse", "refs/tags/minor"), state["sha"])

    def test_invalid_release_fails_before_any_remote_change(self):
        for changes in [{"immutable": True}, {"name": ""}, {"body": ""},
                        {"tag_name": "v3.5.2"}, {"draft": True}]:
            with self.subTest(changes=changes):
                with patch.dict(self.release, changes), self.assertRaises(r.ReleaseError):
                    self.prepare()
                self.assertEqual(self.saved, {})
                self.assertEqual(self.remote_git("rev-parse", "main"), self.source)

    def test_changed_notes_on_retry_fail_without_another_bump(self):
        state = self.prepare()
        self.release["body"] = "Changed after preparation"
        with self.assertRaisesRegex(r.ReleaseError, "changed after preparation"):
            self.prepare()
        self.assertEqual(self.remote_git("rev-parse", "main"), state["sha"])

    def test_conflicting_final_tag_is_never_moved(self):
        state = self.prepare()
        self.remote_git("update-ref", "refs/tags/v3.5.2", self.source)
        with self.assertRaisesRegex(r.ReleaseError, "different code"):
            r.finalize_tag(state)
        self.assertEqual(self.remote_git("rev-parse", "refs/tags/v3.5.2"), self.source)
        self.assertEqual(self.release["tag_name"], "fix")


class ArchiveTests(unittest.TestCase):
    def test_zip_timestamps_do_not_change_content_comparison(self):
        def archive(year, content):
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w") as z:
                z.writestr(zipfile.ZipInfo("data", (year, 1, 1, 0, 0, 0)), content)
            return buf.getvalue()
        self.assertEqual(r.jar_content_hash(archive(2025, b"same")), r.jar_content_hash(archive(2026, b"same")))
        self.assertNotEqual(r.jar_content_hash(archive(2025, b"old")), r.jar_content_hash(archive(2025, b"new")))

    def test_multipart_preserves_binary_and_markdown(self):
        body, headers = r.multipart("metadata", {"changelog": 'A "quote"\nSecond line'}, "file.jar", b"\x00\xff\x10")
        self.assertIn(b"\x00\xff\x10", body)
        self.assertIn(b'name="metadata"', body)
        self.assertIn(b'filename="file.jar"', body)
        self.assertIn("multipart/form-data; boundary=", headers["Content-Type"])


if __name__ == "__main__":
    unittest.main()
