#!/usr/bin/env python3
"""Hal fiyatlarini sources.json'daki adreslerden cekip data/ klasorune tarihli JSON olarak yazar.

Iki sayfa bicimi desteklenir:
  parser "list"  : satir = [urun, fiyat]; fiyat hucresinde bir veya iki fiyat olabilir
                   ("40,00 50,00", "35.00₺ 45,00₺"). Iki fiyat = en dusuk / en yuksek.
                   "Pazarlik" yazan satirlar fiyatsiz tutulur, "Aciklanmadi" olanlar atlanir.
  parser "rows5" : satir = [urun, birim, en dusuk, en yuksek, "TL."]; "section" ile verilen
                   hal basligi altindaki satirlar okunur (Mugla-Fethiye sayfasi).
"""
import datetime
import json
import pathlib
import re
import sys
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup, NavigableString

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
CONFIG = ROOT / "scraper" / "sources.json"
UA = "HalFiyatlariApp/1.0 (kisisel kullanim; gunde birkac istek)"

DATE = re.compile(r"\d{1,2}[./-]\d{1,2}[./-]\d{2,4}")
UNIT_CELL = re.compile(r"^(kg|adet|bağ|demet|koli|kasa)$", re.I)
LETTER = re.compile(r"[A-Za-zÇĞİÖŞÜçğıöşü]")


def to_float(s):
    s = s.strip()
    if "," in s:
        s = s.replace(".", "").replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


def numbers(text):
    """Metindeki makul fiyatlari sirayla dondurur."""
    out = []
    for tok in re.findall(r"\d[\d.,]*", text):
        v = to_float(tok.rstrip(".,"))
        if v is not None and 0 < v < 5000:
            out.append(v)
    return out


def tr_title(s):
    """BUYUK HARFLE yazilmis Turkce adlari 'Baslik Bicimi'ne cevirir; diger adlara dokunmaz."""
    s = s.strip().rstrip(".")
    if not s.isupper():
        return s
    s = s.replace("I", "ı").replace("İ", "i").lower()
    out = []
    for w in s.split():
        first = {"i": "İ", "ı": "I"}.get(w[0], w[0].upper())
        out.append(first + w[1:])
    return " ".join(out)


def add_item(items, name, unit, lo, hi):
    key = name.lower()
    new = {"name": name, "unit": unit, "min": lo, "max": hi}
    old = items.get(key)
    if old is None:
        items[key] = new
        return
    width = lambda i: (i["max"] - i["min"]) if i["min"] is not None and i["max"] is not None else -1  # noqa: E731
    if width(new) > width(old):
        items[key] = new


def parse_html(html):
    """parser 'list'"""
    soup = BeautifulSoup(html, "lxml")
    items = {}
    for tr in soup.find_all("tr"):
        cells = [c.get_text(" ", strip=True) for c in tr.find_all(["td", "th"])]
        if len(cells) < 2:
            continue
        name = cells[0]
        low = name.lower()
        if (not LETTER.search(name) or low.startswith("ürün") or "nöbetçi" in low
                or "eczane" in low or DATE.search(name)):
            continue
        unit = "kg"
        rest = []
        for c in cells[1:]:
            if UNIT_CELL.match(c.strip()):
                unit = c.strip().lower()
            else:
                rest.append(c)
        price_text = " ".join(rest)
        nums = numbers(price_text)[:2]
        if nums:
            add_item(items, tr_title(name), unit, min(nums), max(nums))
        elif "pazarlık" in price_text.lower():
            add_item(items, tr_title(name), unit, None, None)
    return list(items.values())


def parse_rows5(html, section):
    """parser 'rows5': [urun, birim, en dusuk, en yuksek, TL.]; yalnizca 'section' hali okunur."""
    soup = BeautifulSoup(html, "lxml")
    items = {}
    active = False
    for tr in soup.find_all("tr"):
        cells = [c.get_text(" ", strip=True) for c in tr.find_all(["td", "th"])]
        if len(cells) == 1 and cells[0]:
            head = cells[0]
            if "TOPTANCI HAL" in head:
                active = section in head
            continue
        if not active or len(cells) < 4:
            continue
        name, unit = cells[0], cells[1].lower()
        if not LETTER.search(name) or not UNIT_CELL.match(unit):
            continue
        lo = numbers(cells[2])
        hi = numbers(cells[3])
        lo = lo[0] if lo else None
        hi = hi[0] if hi else None
        if lo is None and hi is None:
            continue
        if lo is None:
            lo = hi
        if hi is None:
            hi = lo
        if lo > hi:
            lo, hi = hi, lo
        add_item(items, tr_title(name), unit, lo, hi)
    return list(items.values())


MONTHS = {
    "ocak": 1, "şubat": 2, "mart": 3, "nisan": 4, "mayıs": 5, "haziran": 6,
    "temmuz": 7, "ağustos": 8, "eylül": 9, "ekim": 10, "kasım": 11, "aralık": 12,
}
DATE_TR = re.compile(r"\b(\d{1,2})\s+(" + "|".join(MONTHS) + r")\s+(20\d{2})\b")
DATE_DOT = re.compile(r"\b(\d{1,2})\.(\d{1,2})\.(20\d{2})\b")


def _iso(y, m, d):
    try:
        return datetime.date(int(y), int(m), int(d)).isoformat()
    except ValueError:
        return None


def find_dates(text):
    """Metindeki tarihleri (sira, ISO) olarak dondurur. '6 KASIM 2026' ve '04.11.2026' tanir.
    Tireli '06-11-2026' (sitenin kenar cubugundaki bugunun tarihi) bilerek taninmaz."""
    t = text.replace("I", "ı").replace("İ", "i").lower()
    out = []
    for m in DATE_TR.finditer(t):
        iso = _iso(m.group(3), MONTHS[m.group(2)], m.group(1))
        if iso:
            out.append((m.start(), iso))
    for m in DATE_DOT.finditer(t):
        iso = _iso(m.group(3), m.group(2), m.group(1))
        if iso:
            out.append((m.start(), iso))
    return sorted(out)


def _has_price(table):
    for tr in table.find_all("tr"):
        cells = [c.get_text(" ", strip=True) for c in tr.find_all(["td", "th"])]
        if len(cells) >= 2 and any(re.search(r"\d", c) or "pazarlık" in c.lower() for c in cells[1:]):
            return True
    return False


def page_date(html):
    """Fiyat tablolarinin sayfada yazan tarihi (ISO) ya da None.

    Her fiyatli tablo icin once tablonun ilk satirlarindaki tarih, yoksa tablodan hemen onceki
    metindeki son tarih, yoksa onceki tablonun tarihi kullanilir. Birden fazla tarih varsa EN ESKISI
    dondurulur (yaniltmamak icin). Herhangi bir fiyatli tablo tarihsiz kalirsa None."""
    soup = BeautifulSoup(html, "lxml")
    root = soup.body or soup
    buf = []
    carry = None
    dates = []
    for el in root.descendants:
        if isinstance(el, NavigableString):
            if el.parent is not None and el.parent.name in ("script", "style", "noscript"):
                continue
            s = str(el).strip()
            if s:
                buf.append(s)
        elif getattr(el, "name", None) == "table":
            inner = find_dates(el.get_text(" ", strip=True)[:300])
            pre = find_dates(" ".join(buf[-80:]))
            if inner:
                carry = inner[0][1]
            elif pre:
                carry = pre[-1][1]
            buf = []
            if _has_price(el):
                dates.append(carry)
    if not dates or any(d is None for d in dates):
        return None
    return min(dates)


def same_prices(a, b):
    """Iki listedeki fiyatli urunler birebir ayniysa True (kaynak guncellenmemis olabilir)."""
    def key(items):
        return sorted((i["name"].lower(), i["min"], i["max"]) for i in items if i["min"] is not None)
    ka = key(a)
    return bool(ka) and ka == key(b)


def _mid(i):
    v = [x for x in (i.get("min"), i.get("max")) if x is not None]
    return sum(v) / len(v) if v else None


def add_trends(items, prev_items):
    """Onceki kayda gore fiyati artan urune trend="up", dusene "down" ekler (degismediyse eklemez)."""
    old = {}
    for i in prev_items:
        m = _mid(i)
        if m is not None:
            old[(i["name"].lower(), i.get("unit", "kg"))] = m
    for i in items:
        i.pop("trend", None)
        m = _mid(i)
        o = old.get((i["name"].lower(), i.get("unit", "kg")))
        if m is None or o is None:
            continue
        if m > o + 0.005:
            i["trend"] = "up"
        elif m < o - 0.005:
            i["trend"] = "down"
    return items


def stale_since(items, history):
    """history: (tarih, urunler) en yeniden eskiye. Fiyatlar kac gundur ayniysa en eski gunun tarihi.
    Verisiz gunler atlanir; fiyat farkli cikan ilk gunde durulur."""
    since = None
    for d, old in history:
        if not old:
            continue
        if not same_prices(items, old):
            break
        since = d
    return since


def fetch(url):
    r = requests.get(url, headers={"User-Agent": UA}, timeout=30)
    r.raise_for_status()
    r.encoding = "utf-8"
    return r.text


def parse_source(src, html):
    """(urunler, kaynaktaki fiyat tarihi) dondurur."""
    if src.get("parser") == "rows5":
        items = parse_rows5(html, src["section"])
    else:
        items = parse_html(html)
    return items, page_date(html)


def main():
    cfg = json.loads(CONFIG.read_text("utf-8"))
    today = datetime.datetime.now(ZoneInfo("Europe/Istanbul")).date().isoformat()
    DATA.mkdir(exist_ok=True)
    day_file = DATA / f"{today}.json"
    existing = json.loads(day_file.read_text("utf-8")) if day_file.exists() else {"date": today, "halls": []}
    by_id = {h["id"]: h for h in existing["halls"]}

    # Bir onceki kayitli gunun fiyatlari (ayni fiyatlari tespit etmek icin)
    prev_dates = sorted((p.stem for p in DATA.glob("????-??-??.json") if p.stem < today), reverse=True)
    hist = []  # (tarih, {hal_id: hal}) en yeniden eskiye
    for d in prev_dates[:10]:
        hist.append((d, {h["id"]: h for h in json.loads((DATA / f"{d}.json").read_text("utf-8")).get("halls", [])}))
    prev = {}  # her hal icin, fiyati olan en son onceki kayit
    for d, halls in hist:
        for hid, h in halls.items():
            if h.get("items") and hid not in prev:
                prev[hid] = h

    ok = 0
    for hall in cfg["halls"]:
        items, used, note, sdate = [], "", "", None
        for src in hall["sources"]:
            try:
                items, sdate = parse_source(src, fetch(src["url"]))
                if items:
                    used, note = src["url"], src.get("note", "")
                    break
                print(f"[{hall['id']}] {src['url']}: fiyat bulunamadi", file=sys.stderr)
            except Exception as e:  # noqa: BLE001
                print(f"[{hall['id']}] {src['url']}: hata: {e}", file=sys.stderr)
        if items:
            prev_items = prev.get(hall["id"], {}).get("items", [])
            stale = same_prices(items, prev_items)
            since = stale_since(items, [(d, hs.get(hall["id"], {}).get("items", [])) for d, hs in hist]) if stale else None
            add_trends(items, prev_items)
            by_id[hall["id"]] = {
                "id": hall["id"], "name": hall["name"], "source": used, "note": note,
                "source_date": sdate, "stale": stale, "stale_since": since, "items": items,
            }
            ok += 1
            print(f"[{hall['id']}] {len(items)} urun, kaynak tarihi: {sdate or 'YOK'}"
                  + (f" (GUNCELLENMEDI: {since} kaydiyla ayni)" if stale else ""))
        elif by_id.get(hall["id"], {}).get("source") not in [s["url"] for s in hall["sources"]]:
            by_id[hall["id"]] = {
                "id": hall["id"], "name": hall["name"], "source": "",
                "note": hall.get("empty_note", "Bugün için kaynakta fiyat yok"), "items": [],
            }

    if ok == 0 and not day_file.exists():
        print("Hicbir haldan veri alinamadi; dosya yazilmadi.", file=sys.stderr)
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
