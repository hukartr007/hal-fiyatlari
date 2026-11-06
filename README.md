# Hal Fiyatları (Android)

Kumluca, Serik, Demre, Elmalı, Finike ve Mersin hal fiyatlarını gösteren Android uygulaması.
Günlük fiyat, tarih seçerek geçmişe bakma ve günlük bildirim özellikleri vardır.

## Nasıl çalışır

1. **GitHub Actions** her gün Türkiye saatiyle 10:00, 10:15, 10:30, 10:45, 11:00, 11:15, 11:30 ve 12:00'de `scraper/scrape.py` çalıştırır, fiyatları `data/YYYY-MM-DD.json` olarak depoya kaydeder (değişiklik yoksa yeni kayıt oluşmaz). GitHub zamanlanmış görevleri tam saatinde çalıştırmayı garanti etmez; birkaç dakika gecikebilir, bazen bir çalıştırma atlanır. Saatleri `.github/workflows/scrape.yml` içindeki `cron` satırlarından değiştirebilirsin (saatler UTC; Türkiye = UTC+3).
2. **Uygulama** bu JSON dosyalarını GitHub'dan okur. Geçmiş tarihler `data/` klasöründeki arşivdir.
3. **Bildirim:** uygulama 3 saatte bir uyanır; ayarladığın saat geçtiyse ve yeni gün verisi varsa favori (★) ürünlerin fiyatını bildirir. Ayarlar'dan bildirimde hangi hallerin gösterileceğini seçebilirsin (varsayılan: hepsi).
4. **Eski veri uyarısı:** Bir halin fiyatları bir önceki kayıtla birebir aynıysa kaynak güncellenmemiş olabilir. Uygulama o halin sayfasında uyarı gösterir, bildirimde "(değişmedi)" yazar.

## Kurulum (bir kez)

1. GitHub'da **herkese açık (public)** bir depo aç, adı `hal-fiyatlari` olsun. (Uygulama dosyalara `raw.githubusercontent.com` üzerinden erişir; gizli depoda çalışmaz.)
2. Bu klasörün içeriğini depoya yükle. `.github` klasörü de dahil olmalı. Komut satırıyla:
   ```
   git init -b main
   git add .
   git commit -m "ilk sürüm"
   git remote add origin https://github.com/KULLANICI/hal-fiyatlari.git
   git push -u origin main
   ```
3. Depoda **Settings → Actions → General → Workflow permissions → Read and write permissions** seç, kaydet.
4. **Actions** sekmesi → **Fiyatları topla** → **Run workflow**. Bitince `data/` klasöründe bugünün dosyası oluşur. Kırmızı hata varsa log'a bak (aşağıdaki "Veri kaynakları" bölümü).
5. **Actions** → **APK derle** → **Run workflow** (ilk yüklemede kendiliğinden de başlar). Bitince çalıştırmanın sayfasında **Artifacts → HalFiyatlari-apk** indir, zip'in içindeki `app-debug.apk` dosyasını telefona gönder ve kur ("bilinmeyen kaynaklardan yüklemeye izin ver" gerekir).

## Tekrar bildirim ve SMS

- **Fiyat değişince tekrar bildir** (Ayarlar'da, varsayılan açık): Telefon 30 dakikada bir yeni veri var mı bakar. Favori (★) ürünlerinin fiyatı gün içinde değişirse tekrar bildirir. Kapalıyken günde yalnızca bir kez, 3 saatte bir kontrolle bildirilir. Açıkken pil kullanımı biraz artar.
- **SMS:** Ayarlar'da "SMS de gönder"i aç, numaraları virgülle yaz (örn. `0532 111 22 33, 0533 444 55 66`). SMS telefondaki hattan gönderilir, operatör ücreti uygulanır; telefonun açık ve internete bağlı olması gerekir. Yalnızca favori ürünlerin fiyatı gönderilir, mesaj Türkçe karaktersiz yazılır (SMS ücretini düşürmek için).
- Android 13 ve üstünde, APK'yı Play Store dışından yüklediğin için SMS izni engelli görünebilir. Böyle olursa: Ayarlar → Uygulamalar → Hal Fiyatları → sağ üstteki ⋮ → **Kısıtlı ayarlara izin ver**, sonra uygulamada SMS'i tekrar kaydet.
- Bildirim ve SMS yalnızca telefon uygulaması açıkken değil, arka planda da çalışır; ama bazı telefonlarda pil tasarrufu ayarı arka plan işlerini durdurabilir. Böyle olursa uygulamanın pil ayarını "kısıtlama yok"a al.

## Windows'ta kendi bilgisayarında derleme

Bilgisayarında Android SDK (`%USERPROFILE%\android-sdk`), Gradle 8.9 (`%USERPROFILE%\gradle-8.9`) ve JDK 17/21 varsa GitHub'a gerek kalmadan derleyebilirsin:

1. Zip'i bir klasöre çıkar (Masaüstü yerine örn. `C:\hal-fiyatlari`; yol boşluksuz ve kısa olsun).
2. `APK-DERLE.bat` dosyasına çift tıkla, GitHub kullanıcı adını yaz.
3. Bitince Masaüstünde `HalFiyatlari.apk` oluşur.

Uygulamanın fiyat göstermesi için yine de yukarıdaki GitHub kurulumunun (depo + "Fiyatları topla" görevi) yapılmış olması gerekir; derleme yalnızca APK'yı üretir.

## Veri kaynakları

`scraper/sources.json` her hal için bir veya birkaç sayfa adresi içerir; ilk sonuç veren kullanılır. Varsayılan adresler karekod.org sayfalarıdır ve **doğrulanmamıştır**:

- Sayfa kaynağını belirtmiyor, fiyatlar "referans" niteliğinde ve tarihsiz olabilir; resmi bülten değildir.
- Bazı siteler fiyat yerine "Pazarlık" yazar; bu satırlar atlanır.
- Bir hal için adres yanlışsa veya tablo bulunamazsa o hal uygulamada "veri yok" görünür. `sources.json`'daki adresi düzeltmen yeterli; uygulamayı yeniden derlemen gerekmez.
- Başka bir siteyi eklemek için `sources` listesine `{"url": "...", "note": "..."}` ekle. Betik, başlığında ürün/fiyat (veya en düşük/en yüksek) geçen HTML tablolarını okur. Farklı yapıdaki sayfalar için `scraper/scrape.py` içindeki `parse_html` uyarlanmalıdır.
- Siteleri günde yalnızca bir kez çağırır. Kullanmadan önce her sitenin kullanım şartlarını kontrol et.

Geçmiş veri yalnızca betiğin çalışmaya başladığı günden itibaren birikir.

## Yerelde test

```
pip install requests beautifulsoup4 lxml
python scraper/test_parse.py     # ayrıştırıcı testi
python scraper/scrape.py         # data/ klasörüne yazar
```

## Notlar

- Bu proje yazıldığı ortamda derlenemedi (Android SDK indirilemedi); ilk derlemede küçük bir hata çıkabilir. Hata olursa Actions log'unu bana yapıştır.
- GitHub, 60 gün boyunca hiç etkinlik olmayan genel depolarda zamanlanmış görevleri durdurur. Günlük veri kaydı etkinlik sayılır, ama durduğunu görürsen Actions sekmesinden yeniden etkinleştir.
