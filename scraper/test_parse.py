"""Calistir: python scraper/test_parse.py  (pip install requests beautifulsoup4 lxml)"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from scrape import parse_html, parse_rows5, same_prices, tr_title  # noqa: E402

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
print("tamam")
