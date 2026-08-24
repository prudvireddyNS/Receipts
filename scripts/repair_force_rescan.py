#!/usr/bin/env python3
"""Remove rows introduced by the 2026-08-24 bulk-rescan bug without restoring user-deleted rows."""

import argparse
import shutil
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path


def ids(connection: sqlite3.Connection, table: str, key: str) -> set[str]:
    return {row[0] for row in connection.execute(f"SELECT {key} FROM {table}")}


def set_string(root: ET.Element, name: str, value: str) -> None:
    node = next((child for child in root if child.attrib.get("name") == name), None)
    if node is None:
        node = ET.SubElement(root, "string", {"name": name})
    node.text = value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--current-db", required=True, type=Path)
    parser.add_argument("--current-prefs", required=True, type=Path)
    parser.add_argument("--verified-db", required=True, type=Path)
    parser.add_argument("--bad-scan-db", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    repaired_db = args.output_dir / "track_budget.db"
    repaired_prefs = args.output_dir / "track_budget.xml"
    shutil.copy2(args.current_db, repaired_db)

    verified = sqlite3.connect(f"file:{args.verified_db}?mode=ro", uri=True)
    bad_scan = sqlite3.connect(f"file:{args.bad_scan_db}?mode=ro", uri=True)
    repaired = sqlite3.connect(repaired_db)
    try:
        verified_transaction_ids = ids(verified, "transactions", "id")
        cutoff = verified.execute("SELECT MAX(occurred_at) FROM transactions").fetchone()[0]
        bad_scan_rows = bad_scan.execute("SELECT id, occurred_at FROM transactions").fetchall()
        bad_transaction_ids = [
            transaction_id
            for transaction_id, occurred_at in bad_scan_rows
            if transaction_id not in verified_transaction_ids and occurred_at <= cutoff
        ]
        verified_stamps = ids(verified, "stamps", "id")
        false_stamps = ids(bad_scan, "stamps", "id") - verified_stamps
        verified_snapshots = ids(verified, "period_snapshots", "period_key")
        false_snapshots = ids(bad_scan, "period_snapshots", "period_key") - verified_snapshots

        with repaired:
            repaired.executemany("DELETE FROM transactions WHERE id = ?", ((value,) for value in bad_transaction_ids))
            repaired.executemany("DELETE FROM stamps WHERE id = ?", ((value,) for value in false_stamps))
            repaired.executemany("DELETE FROM period_snapshots WHERE period_key = ?", ((value,) for value in false_snapshots))
            repaired.execute("DELETE FROM dismissed_drops")
        if repaired.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("SQLite integrity check failed")
        print(f"deleted_transactions={len(bad_transaction_ids)}")
        print(f"deleted_stamps={len(false_stamps)}")
        print(f"deleted_snapshots={len(false_snapshots)}")
        print(f"remaining_transactions={repaired.execute('SELECT COUNT(*) FROM transactions').fetchone()[0]}")
    finally:
        repaired.close()
        bad_scan.close()
        verified.close()

    tree = ET.parse(args.current_prefs)
    root = tree.getroot()
    remove_names = {
        "category_limit_alerts",
        "delivered_drops",
        "drop_batch",
        "drop_batch_keys",
        "review_count",
        "review_period",
    }
    for child in list(root):
        name = child.attrib.get("name", "")
        if name in remove_names or name.startswith("review_peak_"):
            root.remove(child)
    set_string(root, "rhythm", "MONTHLY")
    set_string(root, "budget_period", "Month")
    ET.indent(tree, space="    ")
    tree.write(repaired_prefs, encoding="utf-8", xml_declaration=True)
    print(f"repaired_db={repaired_db}")
    print(f"repaired_prefs={repaired_prefs}")


if __name__ == "__main__":
    main()
