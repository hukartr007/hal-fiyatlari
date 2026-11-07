"""Calistir: python scraper/test_parse.py  (pip install requests beautifulsoup4 lxml)"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from scrape import add_trends, find_dates, page_date, parse_html, parse_rows5, parse_source, same_prices, tr_title  # noqa: E402

# batiakdeniztv "list" bicimi: ilk tabloda basliklar, sonrakilerde bos baslik satiri
LIST = """
<table>
<tr><th>Ürünler</th><th>Fiyat (₺/kg)</th></tr>
<tr><td>Domates</td><td>40,00 50,00</td></tr>
<tr><td>Yuvarlak Domates</td><td>80,00 100,00</td></tr>
</table>
<table>
<tr><td></td><td></td></tr>
<tr><td>Çarli Biber</td><td>40.00₺</td></tr>
<tr><td>Sivri Biber</td><td>35.00₺ 45,00₺</td></tr>
<tr><td>Şili Biber Yeşil</td><td>Açıklanmadı</td></tr>
<tr><td>Kabak</td><td>30,00</td></tr>
<tr><td>Domates</td><td>37,00 TL</td></tr>
<tr><td>Alanya Nöbetçi Eczaneler</td></tr>
</table>
<table>
<tr><th>Ürün</th><th>Fiyat (₺/kg)</th></tr>
<tr><td>Salatalık</td><td>Pazarlık</td></tr>
<tr><td>DOMATES SALKIM</td><td>55,00 TL</td></tr>
</table>
"""

d = {i["name"]: i for i in parse_html(LIST)}
assert d["Domates"]["min"] == 40.0 and d["Domates"]["max"] == 50.0, d  # genis aralik korunur
assert d["Yuvarlak Domates"]["min"] == 80.0 and d["Yuvarlak Domates"]["max"] == 100.0, d
assert d["Çarli Biber"]["min"] == d["Çarli Biber"]["max"] == 40.0, d
assert d["Sivri Biber"]["min"] == 35.0 and d["Sivri Biber"]["max"] == 45.0, d
assert d["Kabak"]["min"] == 30.0, d
assert "Şili Biber Yeşil" not in d, d  # Aciklanmadi atlanir
assert d["Salatalık"]["min"] is None and d["Salatalık"]["max"] is None, d  # Pazarlik fiyatsiz tutulur
assert d["Domates Salkım"]["min"] == 55.0, d  # BUYUK HARF -> Baslik Bicimi
assert not any("Eczane" in n for n in d), d

# Muglas-Fethiye "rows5" bicimi
ROWS5 = """
<table>
<tr><td>MUĞLA TOPTANCI HALLERİ</td></tr>
<tr><td>FETHİYE KARAÇULHA TOPTANCI HALİ</td></tr>
<tr><td>SEBZE FİYATLARI</td></tr>
<tr><td>ÜRÜN CİNSİ</td><td></td><td>FİYATI</td></tr>
<tr><td>Domates</td><td>kg</td><td>40</td><td>55</td><td>TL.</td></tr>
<tr><td>Domates Diğer</td><td>kg</td><td>25</td><td></td><td>TL.</td></tr>
<tr><td>Barbunya</td><td>kg</td><td></td><td></td><td>TL.</td></tr>
<tr><td>Maydanoz</td><td>bağ</td><td>6</td><td>10</td><td>TL.</td></tr>
<tr><td>MEYVE FİYATLARI</td></tr>
<tr><td>Elma</td><td>kg</td><td>30</td><td>40</td><td>TL.</td></tr>
<tr><td>KARADERE - KUMLUOVA TOPTANCI HALİ</td></tr>
<tr><td>Domates</td><td>kg</td><td>1</td><td>2</td><td>TL.</td></tr>
</table>
"""
r = {i["name"]: i for i in parse_rows5(ROWS5, "FETHİYE")}
assert r["Domates"] == {"name": "Domates", "unit": "kg", "min": 40.0, "max": 55.0}, r
assert r["Domates Diğer"]["min"] == r["Domates Diğer"]["max"] == 25.0, r
assert "Barbunya" not in r, r
assert r["Maydanoz"]["unit"] == "bağ", r
assert r["Elma"]["max"] == 40.0, r
assert len(r) == 4, r  # diger hallerin satirlari (Karadere) alinmaz

# eski veri uyarisi
a = parse_html(LIST)
assert same_prices(a, parse_html(LIST))
assert not same_prices(a, [])
assert not same_prices([], a)
only_pazarlik = [{"name": "Domates", "unit": "kg", "min": None, "max": None}]
assert not same_prices(only_pazarlik, only_pazarlik)  # fiyatsiz liste "eski" sayilmaz

assert tr_title("ÇARLİ BİBER") == "Çarli Biber", tr_title("ÇARLİ BİBER")
assert tr_title("KAPYA BİBER.") == "Kapya Biber"
assert tr_title("Salkım Domates") == "Salkım Domates"
# --- kaynak sayfasindaki fiyat tarihi ---
assert find_dates("6 KASIM 2026 Son Güncelleme 11:00") == [(0, "2026-11-06")]
assert find_dates("04.11.2026 ALANYA TOPTANCI HALİ")[0][1] == "2026-11-04"
assert find_dates("12 EKiM 2026")[0][1] == "2026-10-12"  # Latin i ile yazilmis
assert find_dates("06-11-2026 USD 50,15") == []  # kenar cubugundaki bugunun tarihi sayilmaz
assert find_dates("Serik Hal Fiyatları 19 Ekim") == []  # yilsiz tarih sayilmaz

SIDEBAR = "<div>06-11-2026 USD 50,15 ALTIN 6556</div>"
PRICES = "<tr><td>Domates</td><td>40,00 50,00</td></tr><tr><td>Kabak</td><td>30,00</td></tr>"

# Serik tipi: baslikta tarih tablonun disinda, ikinci tablo tarihsiz -> ilk tablonun tarihi gecerli
SERIK = SIDEBAR + "<h1>Serik Hal Fiyatları</h1><h2>4 KASIM 2026</h2><p>Son Güncelleme: 11:00</p>" \
    "<table>" + PRICES + "</table><table>" + PRICES + "</table><p>Serik Hal Fiyatları 19 Ekim</p>"
assert page_date(SERIK) == "2026-11-04", page_date(SERIK)

# Alanya tipi: tarih tablonun ilk satirinda; ikinci tabloda baska tarih -> en eskisi
ALANYA = "<table><tr><td>6 KASIM 2026 KONAKLI HAL FİYATLARI Son Güncelleme 10:30</td></tr>" + PRICES + "</table>" \
    "<p>04.11.2026 ALANYA TOPTANCI HALİ</p><table>" + PRICES + "</table>"
assert page_date(ALANYA) == "2026-11-04", page_date(ALANYA)

# bugunun tarihli sayfa + fiyatsiz (Aciklanmadi) tablo sayilmaz
OK = SIDEBAR + "<h2>6 KASIM 2026</h2><table>" + PRICES + "</table>" \
    "<p>1 OCAK 2020</p><table><tr><td>Domates</td><td>Açıklanmadı</td></tr></table>"
assert page_date(OK) == "2026-11-06", page_date(OK)

# tarihsiz sayfa (Elmali tipi) -> None
assert page_date(SIDEBAR + "<table>" + PRICES + "</table>") is None

# Mersin/karekod tipi: tarih sutunu tablonun icinde
MERSIN = "<table><tr><th>Tarih</th><th>Ürün</th><th>Min</th></tr>" \
    "<tr><td>28.10.2026</td><td>Domates</td><td>5,50</td></tr></table>"
assert page_date(MERSIN) == "2026-10-28", page_date(MERSIN)

items, sd = parse_source({}, SERIK)
assert sd == "2026-11-04" and len(items) == 2, (items, sd)
items, sd = parse_source({"parser": "rows5", "section": "FETHİYE"}, ROWS5)
assert sd is None and len(items) == 4, (items, sd)  # ROWS5 ornegi tarihsiz

# trend oklari
old = [{"name": "Domates", "unit": "kg", "min": 10, "max": 12},
       {"name": "Biber", "unit": "kg", "min": 20, "max": 20},
       {"name": "Salatalik", "unit": "kg", "min": 8, "max": 10},
       {"name": "Kabak", "unit": "kg", "min": None, "max": None}]
new = [{"name": "domates", "unit": "kg", "min": 12, "max": 14},
       {"name": "Biber", "unit": "kg", "min": 15, "max": 16},
       {"name": "Salatalik", "unit": "kg", "min": 8, "max": 10},
       {"name": "Kabak", "unit": "kg", "min": 5, "max": 6},
       {"name": "Yeni", "unit": "kg", "min": 5, "max": 6}]
add_trends(new, old)
assert [i.get("trend") for i in new] == ["up", "down", None, None, None], new
print("tamam")
