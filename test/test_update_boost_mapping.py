#!/usr/bin/env python3
"""Focused updater tests; the official-APK recovery replay is opt-in."""

import contextlib
import copy
import gzip
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import update_boost_mapping as tool


def method(owner, name, code, offsets, registers=2, parameters=(), returns="I", flags=1, tries=()):
    value = {
        "id": owner + "->" + name + "(" + "".join(parameters) + ")" + returns,
        "name": name, "parameters": list(parameters), "returns": returns, "flags": flags,
    }
    if code is not None:
        value.update(code=code, offsets=offsets, registers=registers, tries=list(tries))
    return value


def inventory(owner, methods, fields=(), **metadata):
    return tool.Inventory([{
        "type": owner, "source": "Client.java", "flags": 1, "parent": "Ljava/lang/Object;",
        "interfaces": [], "methods": methods, "fields": list(fields),
    }], {
        "version": "1.0", "apk_sha256": "a" * 64, "resources": {}, **metadata,
    })


def field(owner, name):
    return {"id": owner + "->" + name + ":I", "name": name, "type": "I", "flags": 1}


def getter(owner, name, native_field):
    return method(owner, name, [
        {"op": "IGET", "regs": [0, 1], "ref": ["field", owner + "->" + native_field + ":I"]},
        {"op": "RETURN", "regs": [0]},
    ], [0, 2, 3])


def client(owner, first, second, names, include_context=True):
    methods = [getter(owner, names[0], first), getter(owner, names[1], second)]
    if include_context:
        methods.append(method(owner, names[2], [
            {"op": "IGET", "regs": [0, 2], "ref": ["field", owner + "->" + first + ":I"]},
            {"op": "IGET", "regs": [1, 2], "ref": ["field", owner + "->" + second + ":I"]},
            {"op": "SUB_INT", "regs": [0, 0, 1]},
            {"op": "RETURN", "regs": [0]},
        ], [0, 2, 4, 6, 7], registers=3))
    return inventory(owner, methods, [field(owner, first), field(owner, second)])


class UpdaterTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="boost-mapping-test-")
        self.root = Path(self.directory.name)
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(tool.Canonical.clear_caches)

    def mapping(self, text, name="mapping.txt"):
        path = self.root / name
        path.write_text(text, encoding="utf-8")
        return tool.Mapping(path)

    def client_mapping(self, owner="old", names=("a", "b", "c"), first="F", second="E", context=True):
        return self.mapping(
            f"example.Client -> {owner}:\n"
            f"    int number -> {first}\n"
            f"    int alpha -> {second}\n"
            f"    int getNumber() -> {names[0]}\n"
            f"    int getAlpha() -> {names[1]}\n"
            + (f"    int difference() -> {names[2]}\n" if context else ""),
            name=owner + "-mapping.txt",
        )

    def run_matcher(self, old, new, mapping):
        matcher = tool.Matcher(old, new, tool.verify_mapping(mapping, old))
        with contextlib.redirect_stderr(io.StringIO()):
            matcher.run()
        return matcher

    def test_register_encodings_branches_resources_and_handlers(self):
        before = method("Lold;", "a", [
            {"op": "IGET", "regs": [0, 2], "ref": ["field", "Lold;->F:I"]},
            {"op": "CONST_4", "regs": [1], "literal": 5},
            {"op": "ADD_INT", "regs": [0, 0, 1]},
            {"op": "IF_LE", "regs": [0, 3], "offset": 3},
            {"op": "RETURN", "regs": [0]},
            {"op": "RETURN", "regs": [3]},
        ], [0, 2, 3, 5, 7, 8, 9], registers=4, parameters=("Lold;", "I"), flags=9,
            tries=((0, 5, [{"type": "Ljava/lang/Throwable;", "address": 8}]),))
        after = copy.deepcopy(before)
        after.update(id="Lnew;->z(Lnew;I)I", name="z", parameters=["Lnew;", "I"], registers=8,
                     offsets=[0, 2, 4, 6, 8, 9, 10],
                     tries=[[0, 6, [{"type": "Ljava/lang/Throwable;", "address": 9}]]])
        renumber = {0: 3, 1: 0, 2: 6, 3: 7}
        for insn in after["code"]:
            insn["regs"] = [renumber[number] for number in insn["regs"]]
        after["code"][0]["ref"][1] = "Lnew;->E:I"
        after["code"][1]["op"] = "CONST_16"

        def fingerprint(value, resources=None):
            owner = tool.split_member(value["id"])[0]
            data = inventory(owner, [value], resources=resources or {})
            return tool.Canonical(data, {}, {}, {}, "old").method_key(value["id"])

        self.assertEqual(fingerprint(before), fingerprint(after))
        for change in ("literal", "dataflow", "handler", "branch"):
            with self.subTest(change=change):
                changed = copy.deepcopy(after)
                if change == "literal":
                    changed["code"][1]["literal"] = 6
                elif change == "dataflow":
                    changed["code"][2]["regs"][2] = 7
                elif change == "handler":
                    changed["tries"][0][2][0]["type"] = "Ljava/lang/Exception;"
                else:
                    changed["code"][3]["offset"] = 2
                self.assertNotEqual(fingerprint(before), fingerprint(changed))
        before["code"][1]["literal"] = 0x7f080001
        after["code"][1]["literal"] = 0x7f080092
        self.assertEqual(fingerprint(before, {0x7f080001: "id/profile"}),
                         fingerprint(after, {0x7f080092: "id/profile"}))
        self.assertNotEqual(fingerprint(before, {0x7f080001: "id/profile"}),
                            fingerprint(after, {0x7f080092: "id/inbox"}))

    def test_array_owner_and_wide_parameter_normalization(self):
        keys = []
        for owner in ("Lold;", "Lnew;"):
            value = method(owner, "values", [
                {"op": "IGET_OBJECT", "regs": [0, 0], "ref": ["field", owner + "->table:[" + owner]},
                {"op": "INVOKE_VIRTUAL", "regs": [0], "ref": ["method", "[" + owner + "->clone()Ljava/lang/Object;"]},
                {"op": "MOVE_RESULT_OBJECT", "regs": [0]},
                {"op": "CHECK_CAST", "regs": [0], "ref": ["type", "[" + owner]},
                {"op": "RETURN_OBJECT", "regs": [0]},
            ], [0, 2, 5, 6, 8, 9], registers=1, returns="[" + owner)
            keys.append(tool.Canonical(inventory(owner, [value]), {}, {}, {}, "old").method_key(value["id"]))
        self.assertEqual(*keys)
        value = method("Lold;", "a", [{"op": "RETURN_WIDE", "regs": [1]}], [0, 1],
                       registers=4, parameters=("J", "I"), returns="J", flags=9)
        canonical = tool.Canonical(inventory("Lold;", [value]), {}, {}, {}, "old")
        self.assertEqual(("p0",), canonical.code(value["id"])[0][0]["regs"])

    def test_shared_switch_payload_preserves_every_origin(self):
        before = method("Lold;", "a", [
            {"op": "PACKED_SWITCH", "regs": [0], "offset": 21},
            {"op": "CONST_4", "regs": [1], "literal": 1},
            {"op": "GOTO_32", "offset": 4},
            {"op": "CONST_4", "regs": [1], "literal": 2},
            {"op": "RETURN", "regs": [1]},
            {"op": "GOTO_32", "offset": 3},
            {"op": "PACKED_SWITCH", "regs": [0], "offset": 9},
            {"op": "CONST_4", "regs": [1], "literal": 1},
            {"op": "RETURN", "regs": [1]},
            {"op": "NOP"}, {"op": "NOP"}, {"op": "NOP"},
            {"op": "RETURN", "regs": [1]},
            {"op": "PACKED_SWITCH_PAYLOAD", "switch": [[1, 8]]},
        ], [0, 3, 4, 7, 8, 9, 12, 15, 16, 17, 18, 19, 20, 21, 27],
            registers=2, parameters=("I", "I"), flags=9)
        after = copy.deepcopy(before)
        after["code"][1]["op"] = "CONST_16"
        after["code"][5].update(op="GOTO_16", offset=2)
        after["offsets"] = [0, 3, 5, 8, 9, 10, 12, 15, 16, 17, 18, 19, 20, 21, 27]
        left = tool.Canonical(inventory("Lold;", [before]), {}, {}, {}, "old")
        right = tool.Canonical(inventory("Lold;", [after]), {}, {}, {}, "old")
        self.assertEqual([(0, [(1, 4)]), (6, [(1, 12)])], left.code(before["id"])[0][-1]["switches"])
        self.assertNotEqual(left.method_key(before["id"]), right.method_key(after["id"]))

    def test_duplicate_getters_are_unresolved_until_field_accesses_disambiguate(self):
        mapping = self.client_mapping(context=False)
        old = client("Lold;", "F", "E", ("a", "b", "c"), include_context=False)
        new = client("Lnew;", "E", "F", ("x", "y", "z"), include_context=False)
        matcher = self.run_matcher(old, new, mapping)
        self.assertEqual({}, matcher.tables["methods"])
        self.assertEqual({}, matcher.tables["fields"])
        report = matcher.report()
        self.assertFalse(report["mapping_ready"])
        self.assertEqual(2, len(report["symbols"]["methods"][0]["candidates"]))
        mapping = self.client_mapping()
        old = client("Lold;", "F", "E", ("a", "b", "c"))
        new = client("Lnew;", "E", "F", ("x", "y", "z"))
        matcher = self.run_matcher(old, new, mapping)
        self.assertEqual("Lnew;->E:I", matcher.tables["fields"]["Lold;->F:I"])
        self.assertEqual("Lnew;->x()I", matcher.tables["methods"]["Lold;->a()I"])
        self.assertTrue(matcher.report()["mapping_ready"])

    def test_callsite_can_identify_a_changed_callee_without_claiming_same_body(self):
        snapshots = []
        for owner, callee, literal in (("Lold;", "a", 1), ("Lnew;", "z", 2)):
            snapshots.append(inventory(owner, [
                method(owner, "load", [
                    {"op": "INVOKE_STATIC", "regs": [], "ref": ["method", owner + "->" + callee + "()I"]},
                    {"op": "MOVE_RESULT", "regs": [0]}, {"op": "RETURN", "regs": [0]},
                ], [0, 3, 4, 5], registers=1, flags=9),
                method(owner, callee, [
                    {"op": "CONST_4", "regs": [0], "literal": literal}, {"op": "RETURN", "regs": [0]},
                ], [0, 1, 2], registers=1, flags=9),
            ]))
        mapping = self.mapping("example.Client -> old:\n    int load() -> load\n    int compute() -> a\n")
        matcher = self.run_matcher(*snapshots, mapping)
        self.assertEqual("Lnew;->z()I", matcher.tables["methods"]["Lold;->a()I"])
        self.assertEqual(["Lexample/Client;->compute()I"],
                         [item["canonical"] for item in matcher.report()["changed_method_bodies"]])

    def test_scoring_does_not_feed_target_names_back_into_matching(self):
        mapping = self.client_mapping()
        matcher = self.run_matcher(client("Lold;", "F", "E", ("a", "b", "c")),
                                   client("Lnew;", "E", "F", ("x", "y", "z")), mapping)
        before = copy.deepcopy(matcher.tables)
        report = matcher.report()
        expected = self.client_mapping("new", ("x", "y", "z"), "F", "E")
        tool.compare_expected(report, expected)
        self.assertEqual(before, matcher.tables)
        self.assertEqual(2, len(report["comparison"]["fields"]["mismatches"]))

    def test_mapping_emission_rejects_conflicts_missing_and_invalid_declarations(self):
        mapping = self.client_mapping()
        new = client("Lnew;", "E", "F", ("x", "y", "z"))
        matcher = self.run_matcher(client("Lold;", "F", "E", ("a", "b", "c")), new, mapping)
        report = matcher.report()
        emitted = self.mapping(tool.render_mapping(mapping, report, new), name="emitted.txt")
        tool.verify_mapping(emitted, new)
        matcher.bind("fields", "Lold;->F:I", "Lnew;->F:I", "conflicting-test-witness")
        self.assertFalse(matcher.report()["mapping_ready"])
        with self.assertRaises(tool.MappingError):
            tool.render_mapping(mapping, matcher.report(), new)
        invalid = copy.deepcopy(report)
        invalid["symbols"]["methods"][0]["new"] = "Lwrong;->x()I"
        with self.assertRaisesRegex(tool.MappingError, "incompatible"):
            tool.render_mapping(mapping, invalid, new)
        invalid = copy.deepcopy(report)
        invalid["symbols"]["fields"][1]["new"] = invalid["symbols"]["fields"][0]["new"]
        with self.assertRaisesRegex(tool.MappingError, "Conflicting fields"):
            tool.render_mapping(mapping, invalid, new)
        new.methods["Lnew;->x()I"]["flags"] |= 8
        with self.assertRaisesRegex(tool.MappingError, "static/instance"):
            tool.render_mapping(mapping, report, new)

    def test_non_bijective_and_qualified_source_mappings_are_rejected(self):
        duplicate = self.mapping("example.Client -> old:\n    int number -> F\n    int number -> E\n")
        with self.assertRaisesRegex(tool.MappingError, "conflicting canonical"):
            tool.mapping_symbols(duplicate)
        qualified = self.mapping("example.Client -> old:\n    int example.Other.compute() -> a\n")
        with self.assertRaisesRegex(tool.MappingError, "qualified"):
            tool.mapping_symbols(qualified)

    def test_apk_and_schema_keyed_cache_avoids_rerunning_extraction(self):
        apk = self.root / "source.apk"
        apk.write_bytes(b"first fixture")
        count = 0
        def command(args):
            nonlocal count
            count += 1
            if args[1:3] == ["dump", "badging"]:
                return "package: name='com.rubenmayayo.reddit' versionCode='1' versionName='1.0'\n"
            if args[1:3] == ["dump", "resources"]:
                return "    resource 0x7f010001 id/profile\n"
            paths = dict(value[2:].split("=", 1) for value in args if value.startswith("-P"))
            metadata = json.loads(Path(paths["boostFingerprintMetadata"]).read_text())
            with gzip.open(paths["boostFingerprintOutput"], "wt", encoding="utf-8") as destination:
                destination.write(json.dumps(metadata) + "\n")
                destination.write(json.dumps(inventory("Lold;", []).classes["Lold;"]) + "\n")
            return ""
        with patch.object(tool, "run_command", side_effect=command):
            first = tool.Inventory.load(apk, self.root / "cache", Path("aapt2"))
            second = tool.Inventory.load(apk, self.root / "cache", Path("aapt2"))
            self.assertEqual(3, count)
            self.assertEqual(first.metadata, second.metadata)
            apk.write_bytes(b"second fixture")
            tool.Inventory.load(apk, self.root / "cache", Path("aapt2"))
            self.assertEqual(6, count)
            with patch.object(tool, "CACHE_SCHEMA", tool.CACHE_SCHEMA + 1):
                tool.Inventory.load(apk, self.root / "cache", Path("aapt2"))
            self.assertEqual(9, count)

    def test_failures_preserve_inputs_and_existing_output(self):
        source = self.root / "source.apk"
        source.write_bytes(b"original")
        with contextlib.redirect_stderr(io.StringIO()):
            status = tool.main(["--old-apk", str(source), "--new-apk", str(source),
                                "--old-mapping", str(source), "--report", str(source)])
        self.assertEqual(1, status)
        self.assertEqual(b"original", source.read_bytes())
        output = self.root / "report.json"
        output.write_text("existing", encoding="utf-8")
        with patch.object(tool.os, "replace", side_effect=OSError("write failed")):
            with self.assertRaises(OSError):
                tool.write_atomic(output, "replacement")
        self.assertEqual("existing", output.read_text())


FIXTURE_VARIABLES = ("BOOST_MAPPING_OLD_APK", "BOOST_MAPPING_OLD_MAP", "BOOST_APK", "BOOST_MAPPING_EXPECTED")


@unittest.skipUnless(all(os.environ.get(variable) for variable in FIXTURE_VARIABLES),
                     "Set " + ", ".join(FIXTURE_VARIABLES) + " for an APK/mapping recovery replay")
class ApkRecoveryTest(unittest.TestCase):
    def test_recovered_symbols_match_supplied_mapping(self):
        old_symbols = tool.mapping_symbols(tool.Mapping(Path(os.environ["BOOST_MAPPING_OLD_MAP"])))
        expected_symbols = tool.mapping_symbols(tool.Mapping(Path(os.environ["BOOST_MAPPING_EXPECTED"])))
        with tempfile.TemporaryDirectory(prefix="boost-recovery-test-") as directory:
            report_path = Path(directory) / "report.json"
            result = subprocess.run([
                sys.executable, "-B", str(tool.ROOT / "scripts/update_boost_mapping.py"),
                "--old-apk", os.environ["BOOST_MAPPING_OLD_APK"],
                "--old-mapping", os.environ["BOOST_MAPPING_OLD_MAP"],
                "--new-apk", os.environ["BOOST_APK"],
                "--expected-mapping", os.environ["BOOST_MAPPING_EXPECTED"],
                "--report", str(report_path),
            ], cwd=tool.ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            self.assertIn(result.returncode, (0, 2), result.stdout)
            report = json.loads(report_path.read_text())
            for kind in tool.KINDS:
                with self.subTest(kind=kind):
                    entries = report["symbols"][kind]
                    self.assertEqual(len(old_symbols[kind]), len(entries))
                    self.assertEqual(set(old_symbols[kind]), {item["canonical"] for item in entries})
                    for item in entries:
                        self.assertEqual(old_symbols[kind][item["canonical"]], item["old"])
                        if item["new"] is not None:
                            self.assertIn(item["canonical"], expected_symbols[kind])
                            self.assertEqual(expected_symbols[kind][item["canonical"]], item["new"],
                                             item["canonical"])
                    matched = sum(item["new"] is not None for item in entries)
                    surviving = len(old_symbols[kind].keys() & expected_symbols[kind].keys())
                    self.assertEqual({
                        "total": len(entries), "matched": matched, "unresolved": len(entries) - matched,
                    }, report["summary"][kind])
                    self.assertEqual({
                        "surviving": surviving, "removed": len(entries) - surviving, "correct": matched,
                        "recovery_percent": round(100 * matched / surviving, 2) if surviving else None,
                        "mismatches": [],
                    }, report["comparison"][kind])
            self.assertEqual([], report["conflicts"])
            self.assertEqual(all(not item["unresolved"] for item in report["summary"].values()),
                             report["mapping_ready"])
            self.assertEqual(0 if report["mapping_ready"] and not report["changed_method_bodies"] else 2,
                             result.returncode)
            tool.write_atomic(tool.ROOT / "build/boost-mapping-tests/recovery.json",
                              json.dumps(report, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    unittest.main()
