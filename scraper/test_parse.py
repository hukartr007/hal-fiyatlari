"""Çalıştır: python scraper/test_parse.py  (pip install requests beautifulsoup4 lxml)"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from scrape import parse_html, same_prices  # noqa: E402

# Mersin tipi: Tarih | Ürün | Birim | Min | Max
MERSIN = """<table>
<tr><th>Tarih</th><th>Ürün</th><th>Birim</th><th>Min (TL)</th><th>Max (TL)</th></tr>
<tr><td>28.10.2026</td><td>Domates</td><td>kg</td><td>5,50</td><td>100</td></tr>
<tr><td>28.10.2026</td><td>Maydanoz</td><td>bağ</td><td>4</td><td>15</td></tr>
</table>"""

# Kumluca tipi: Ürün | Son Referans Fiyat, ayrıca Önceki Fiyat
KUMLUCA = """<table>
<tr><th>Ürün</th><th>Son Referans Fiyat</th></tr>
<tr><td>Domates</td><td>20 TL/kg</td></tr>
<tr><td>Sivri Biber</td><td>30 TL/kg</td></tr>
</table>
<table>
<tr><th>Ürün</th><th>Önceki Fiyat</th><th>Fiyat</th></tr>
<tr><td>Patlıcan</td><td>22</td><td>25</td></tr>
</table>"""

# Pazarlık tipi: sayısal fiyat yok
PAZARLIK = """<table><tr><th>Ürün</th><th>Fiyat (₺/kg)</th></tr>
<tr><td>Domates</td><td>Pazarlık</td></tr></table>"""

m = {i["name"]: i for i in parse_html(MERSIN)}
assert m["Domates"] == {"name": "Domates", "unit": "kg", "min": 5.5, "max": 100.0}, m
assert m["Maydanoz"]["unit"] == "bağ" and m["Maydanoz"]["min"] == 4.0, m

k = {i["name"]: i for i in parse_html(KUMLUCA)}
assert k["Domates"]["min"] == k["Domates"]["max"] == 20.0, k
assert k["Patlıcan"]["min"] == 25.0, k  # önceki fiyat (22) değil, güncel fiyat

assert parse_html(PAZARLIK) == []

# kaynak güncellenmediyse (birebir aynı fiyatlar) uyarı işareti
a = parse_html(MERSIN)
assert same_prices(a, parse_html(MERSIN))
assert not same_prices(a, parse_html(KUMLUCA))
assert not same_prices(a, [])
assert not same_prices([], a)
print("tamam")
