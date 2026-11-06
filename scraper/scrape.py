#!/usr/bin/env python3
"""Hal fiyatlarını sources.json'daki adreslerden çekip data/ klasörüne tarihli JSON olarak yazar.

Her kaynak sayfadaki HTML tablolarını okur. Sütunlar başlık satırından tanınır
(ürün / en düşük / en yüksek / fiyat / birim); tanınmazsa ilk sayısal sütun(lar) kullanılır.
"Pazarlık" gibi sayısal olmayan fiyatlar atlanır.
"""
import datetime
import json
import pathlib
import re
import sys
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
CONFIG = ROOT / "scraper" / "sources.json"
UA = "HalFiyatlariApp/1.0 (kisisel kullanim; gunde bir istek)"

NUM = re.compile(r"\d+(?:[.,]\d+)*")
DATE = re.compile(r"\d{1,2}[./-]\d{1,2}[./-]\d{2,4}")
UNIT_CELL = re.compile(r"^(kg|adet|bağ|demet|koli|kasa)$", re.I)
UNIT_HEADER = re.compile(r"(?:₺|tl)\s*/\s*(kg|adet|bağ|demet|koli|kasa)", re.I)
LETTER = re.compile(r"[A-Za-zÇĞİÖŞÜçğıöşü]")


def to_float(s):
    s = s.strip()
    if "," in s:
        s = s.replace(".", "").replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


def cell_value(text):
    """Hücredeki ilk makul fiyatı döndürür; tarih, yüzde, 'Pazarlık' vb. için None."""
    t = text.strip()
    if not t or "pazarlık" in t.lower() or "%" in t or DATE.search(t):
        return None
    m = NUM.search(t)
    if not m:
        return None
    v = to_float(m.group(0))
    if v is None or not (0 < v < 5000):
        return None
    return v


def col_map(header):
    m = {}
    for i, h in enumerate(header):
        h = h.lower()
        if "önceki" in h or "değişim" in h or "fark" in h:
            continue
        if "ürün" in h or "cins" in h:
            m.setdefault("name", i)
        elif "birim" in h:
            m["unit"] = i
        elif "düşük" in h or "min" in h:
            m["min"] = i
        elif "yüksek" in h or "max" in h:
            m["max"] = i
        elif "fiyat" in h or "₺" in h:
            m.setdefault("price", i)
    return m


def parse_html(html):
    soup = BeautifulSoup(html, "lxml")
    items = {}
    for table in soup.find_all("table"):
        rows = table.find_all("tr")
        if not rows:
            continue
        first = [c.get_text(" ", strip=True) for c in rows[0].find_all(["td", "th"])]
        joined = " ".join(first).lower()
        has_header = "ürün" in joined or "fiyat" in joined or "cins" in joined
        cmap = col_map(first) if has_header else {}
        hm = UNIT_HEADER.search(joined)
        default_unit = hm.group(1).lower() if hm else "kg"
        for tr in rows[1 if has_header else 0:]:
            cells = [c.get_text(" ", strip=True) for c in tr.find_all(["td", "th"])]
            if len(cells) < 2:
                continue
            name = cells[cmap.get("name", 0)] if cmap.get("name", 0) < len(cells) else ""
            name = name.strip()
            if not name or not LETTER.search(name) or DATE.search(name):
                continue

            def at(key):
                i = cmap.get(key)
                return cell_value(cells[i]) if i is not None and i < len(cells) else None

            lo, hi = at("min"), at("max")
            if lo is None and hi is None:
                p = at("price")
                if p is not None:
                    lo = hi = p
            if lo is None and hi is None and not cmap:
                vals = [cell_value(c) for i, c in enumerate(cells) if i != 0]
                vals = [v for v in vals if v is not None][:2]
                if vals:
                    lo, hi = min(vals), max(vals)
            if lo is None and hi is None:
                continue
            if lo is not None and hi is not None and lo > hi:
                lo, hi = hi, lo

            unit = default_unit
            ui = cmap.get("unit")
            if ui is not None and ui < len(cells) and UNIT_CELL.match(cells[ui].strip()):
                unit = cells[ui].strip().lower()
            else:
                for c in cells[1:]:
                    if UNIT_CELL.match(c.strip()):
                        unit = c.strip().lower()
                        break
            items[name.lower()] = {"name": name, "unit": unit, "min": lo, "max": hi}
    return list(items.values())


def same_prices(a, b):
    """İki ürün listesi birebir aynı fiyatları içeriyorsa True (kaynak güncellenmemiş olabilir)."""
    def key(items):
        return sorted((i["name"].lower(), i["min"], i["max"]) for i in items)
    return bool(a) and key(a) == key(b)


def fetch(url):
    r = requests.get(url, headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    r.encoding = "utf-8"
    return r.text


def main():
    cfg = json.loads(CONFIG.read_text("utf-8"))
    today = datetime.datetime.now(ZoneInfo("Europe/Istanbul")).date().isoformat()
    DATA.mkdir(exist_ok=True)
    day_file = DATA / f"{today}.json"
    existing = json.loads(day_file.read_text("utf-8")) if day_file.exists() else {"date": today, "halls": []}
    by_id = {h["id"]: h for h in existing["halls"]}

    # Bir önceki kayıtlı günün fiyatları (aynı fiyatları tespit etmek için)
    prev_dates = sorted((p.stem for p in DATA.glob("????-??-??.json") if p.stem < today), reverse=True)
    prev = {}
    if prev_dates:
        prev_data = json.loads((DATA / f"{prev_dates[0]}.json").read_text("utf-8"))
        prev = {h["id"]: h for h in prev_data.get("halls", [])}

    ok = 0
    for hall in cfg["halls"]:
        items, used, note = [], "", ""
        for src in hall["sources"]:
            try:
                items = parse_html(fetch(src["url"]))
                if items:
                    used, note = src["url"], src.get("note", "")
                    break
                print(f"[{hall['id']}] {src['url']}: tabloda fiyat bulunamadı", file=sys.stderr)
            except Exception as e:  # noqa: BLE001
                print(f"[{hall['id']}] {src['url']}: hata: {e}", file=sys.stderr)
        if items:
            stale = same_prices(items, prev.get(hall["id"], {}).get("items", []))
            by_id[hall["id"]] = {
                "id": hall["id"], "name": hall["name"], "source": used, "note": note,
                "stale": stale, "items": items,
            }
            ok += 1
            print(f"[{hall['id']}] {len(items)} ürün" + (" (önceki kayıtla aynı!)" if stale else ""))
        elif hall["id"] not in by_id:
            by_id[hall["id"]] = {
                "id": hall["id"], "name": hall["name"], "source": "",
                "note": "Bugün için veri alınamadı", "items": [],
            }

    if ok == 0 and not day_file.exists():
        print("Hiçbir haldan veri alınamadı; dosya yazılmadı.", file=sys.stderr)
        return 1

    out = {"date": today, "halls": [by_id[h["id"]] for h in cfg["halls"] if h["id"] in by_id]}
    day_file.write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n", "utf-8")

    dates = sorted((p.stem for p in DATA.glob("????-??-??.json")), reverse=True)
    index = {
        "halls": [{"id": h["id"], "name": h["name"]} for h in cfg["halls"]],
        "dates": dates,
        "updated": datetime.datetime.now(ZoneInfo("Europe/Istanbul")).isoformat(timespec="seconds"),
    }
    (DATA / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1) + "\n", "utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
