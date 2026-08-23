# Pusula

Android için sade bir pusula uygulaması. Dış bağımlılığı yok — sadece Android SDK
ve Kotlin standart kütüphanesi kullanılıyor, kadran `Canvas` ile elle çiziliyor.

Gerçek kuzey, kıble yönü, dokununca yön kilitleyen hedef göstergesi ve kadranın
göbeğinde su terazisi.

- `minSdk 24` (Android 7.0) — **Android 13 dahil** tüm sürümlerde çalışır
- `targetSdk 34`
- Paket adı: `com.cem.pusula`
- İzinler: `VIBRATE` (ana yön tıkı) ile `ACCESS_COARSE_LOCATION` ve `ACCESS_FINE_LOCATION` (gerçek kuzey, kıble
  ve koordinat paneli için; reddedilirse uygulama manyetik kuzeyle çalışmaya devam
  eder, "yaklaşık" seçilirse koordinatlar o etiketle gösterilir).

## 0. Klasör düzeni

```
pusula/
├── app/          uygulama kaynağı
├── dist/         üretilen APK'lar          (gitignore'da)
├── docs/         ekran görüntüleri, ikon
├── keys/         imzalama anahtarı         (gitignore'da)
└── keystore.properties                     (gitignore'da)
```

| Ekran | | |
|---|---|---|
| ![Gerçek kuzey](docs/ekran-goruntusu-gercek-kuzey.png) | ![Hedef kilidi](docs/ekran-goruntusu-hedef.png) | ![Manyetik kuzey](docs/ekran-goruntusu.png) |
| Konum izni verilince: gerçek kuzey, manyetik yön, sapma ve kıble; kadranda mavi **M** ile yeşil **Kıble** işaretleri | Kadrana dokununca yön kilitlenir: sarı hat ve "kaç derece sağa/sola" satırı | İzin yokken: manyetik kuzeyle çalışmaya devam eder |

## 1. Hazır APK'yı telefona kurmak

Derlenmiş APK: `app/build/outputs/apk/debug/app-debug.apk` (~800 KB)

### Yöntem A — Kabloyla, adb ile (en hızlı)

Telefonda önce **geliştirici seçenekleri**ni açın:

1. `Ayarlar → Telefon hakkında → Yapı numarası` üstüne 7 kez dokunun.
2. `Ayarlar → Sistem → Geliştirici seçenekleri → USB hata ayıklama` (USB debugging) → açın.
3. Telefonu USB ile bilgisayara bağlayın, ekranda çıkan **"Bu bilgisayara izin ver"**
   uyarısını onaylayın.

Sonra:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
$ANDROID_HOME/platform-tools/adb devices        # telefon "device" olarak görünmeli
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Doğrudan derleyip kurmak için tek komut da yeterli:

```bash
./gradlew installDebug
```

### Yöntem B — Kablosuz adb (Android 11+, kablo gerekmez)

`Geliştirici seçenekleri → Kablosuz hata ayıklama` → **Cihazı eşleştirme kodu ile eşleştir**:

```bash
adb pair <telefon-ip>:<eşleştirme-portu>     # ekrandaki 6 haneli kodu girin
adb connect <telefon-ip>:<bağlantı-portu>    # kablosuz hata ayıklama ekranındaki port
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Yöntem C — Dosyayı telefona kopyalayıp elle kurmak (bilgisayarda adb gerekmez)

APK'yı telefona aktarın (e-posta, Drive, USB bellek, `scp`, ne kolaysa), telefonda
dosya yöneticisinden dosyaya dokunun. Android 13 size **"Bu kaynaktan uygulama
yüklemeye izin ver"** diye soracaktır:

`Ayarlar → Uygulamalar → Özel uygulama erişimi → Bilinmeyen uygulamaları yükle →
(dosya yöneticisi / tarayıcı) → Bu kaynağa izin ver`

İzni verdikten sonra kurulum ekranı açılır. Play Protect "bilinmeyen geliştirici"
uyarısı verirse **Yine de yükle** deyin — kendi imzaladığınız APK olduğu için normaldir.

> Android 13'te, APK'yı bir dosya yöneticisi üzerinden kurduğunuzda uygulama
> "kısıtlı ayarlar" kapsamına girebilir. Erişilebilirlik/bildirim gibi hassas
> ayarlara ihtiyacı olmadığı için bu uygulamada sorun çıkarmaz.

## 2. Kaynaktan derlemek

Gerekli olanlar: JDK 17+ ve Android SDK (platform 34 + build-tools 34.0.0).

```bash
export ANDROID_HOME=$HOME/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties   # yoksa oluşturun
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

Android Studio kullanacaksanız: `File → Open` ile bu klasörü açın, telefonu bağlayın,
yeşil **Run** düğmesine basın — derleme ve kurulum tek adımda yapılır.

### Android SDK'yı komut satırından kurmak (Android Studio olmadan)

```bash
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip commandlinetools-linux-16111833_latest.zip && mv cmdline-tools latest
export ANDROID_HOME=$HOME/Android/Sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 3. Kalıcı kurulum için release APK (önerilir)

Debug APK'ları ortak bir debug anahtarıyla imzalanır. Uygulamayı uzun süre kendi
telefonunuzda tutup güncelleyecekseniz, kendi anahtarınızla imzalanmış bir release
APK üretin — böylece sürüm yükseltmelerinde "imza uyuşmuyor" hatası almazsınız.

```bash
mkdir -p keys
keytool -genkeypair -v -keystore keys/pusula-release.jks -alias pusula \
    -keyalg RSA -keysize 2048 -validity 10000

cat > keystore.properties <<EOF
storeFile=keys/pusula-release.jks
storePassword=...
keyAlias=pusula
keyPassword=...
EOF

./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

`storeFile` yolu proje köküne göre çözülür, bu yüzden proje klasörünü taşımak
ayarı bozmaz.

`keys/`, `dist/` ve `keystore.properties` `.gitignore`'da — commit etmeyin,
**ve `.jks` dosyasını kaybetmeyin**: uygulamayı güncelleyebilmenin tek yolu odur.
`keystore.properties` yoksa build dosyası bu bloğu sessizce atlar, `assembleDebug`
her koşulda çalışır.

## 4. Gerçek kuzey ve manyetik kuzey

Uygulama ikisini birden gösterir:

- **Büyük rakam** gerçek (coğrafi) kuzeye göre yöndür — sapma bilinir bilinmez
  buna geçer, bilinmiyorsa manyetik yönü gösterir ve altındaki etiket hangisi
  olduğunu açıkça yazar.
- **Alt satır** manyetik yönü ve o konumdaki sapmayı verir, örn.
  `Manyetik 265° · Sapma 5,8°D`.
- **Kadranda mavi "M" işareti** manyetik kuzeyin nereye düştüğünü gösterir.
  Kadran gerçek kuzeye göre döndüğü için bu işaret K'dan sapma kadar uzakta durur.

Sapma `GeomagneticField(enlem, boylam, rakım, zaman).declination` ile hesaplanır;
doğuya doğru pozitiftir ve `gerçek = manyetik + sapma` formülüyle uygulanır.
Türkiye'de yaklaşık +5° ile +7° arasındadır.

Bunun için yaklaşık konum yeter, o yüzden yalnızca `ACCESS_COARSE_LOCATION`
isteniyor. Konum `LocationManager`'ın en son bilinen konumundan alınır (Google
Play Hizmetleri gerekmez); bulunan sapma `SharedPreferences`'a yazıldığı için
sonraki açılışlarda gerçek kuzey anında gösterilir. İzin verilmezse alt satır
"Gerçek kuzey için konum izni gerekli" yazar ve dokununca izni yeniden ister;
kalıcı reddedilmişse uygulama ayarlarını açar. İzin olmadan da uygulama
manyetik kuzeyle sorunsuz çalışır.

**Manyetik bozulma uyarısı.** Sapma doğru hesaplansa bile, telefonun yakınındaki
bir mıknatıs ya da mıknatıslanmış metal pusulayı sessizce yanıltır: açı yanlıştır
ama ekranda hiçbir şey belli olmaz. Uygulama bunu yakalar — ölçülen toplam alan
şiddetini o konumda beklenenle (`GeomagneticField.getFieldStrength()`) karşılaştırır,
%25'i aşan sapma **kesintisiz 2,5 saniye** sürerse uyarır, %15'in altına inince
uyarıyı hemen kaldırır:

```
Manyetik bozulma: alan 63 µT, beklenen 49 µT — telefonu metal/mıknatıstan uzaklaştırın.
```

Cihazın kendi hassasiyet bayrağı bu durumu genelde fark etmez, çünkü sabit bir
bozulma "kararlı" görünür. Gerçek bir örnek: bilgisayar masasında ölçülen alan
63,3 µT ve manyetik eğim 9,2° idi — İstanbul'un değerleri 48,5 µT ve 58,7°, o eğim
manyetik ekvatora karşılık gelir. Masadan iki metre uzaklaşınca alan 49,5 µT'ye
oturdu ve yön düzeldi. Bu yüzden rotation-vector'e ek olarak ham manyetometre de
dinlenir (`SENSOR_DELAY_UI`): füzyon yönü verir ama alanın büyüklüğünü vermez.

Süre şartının sebebi: telefonu elde çevirirken kalibrasyon geçici olarak %20'ye
varan sapma üretebiliyor. Süre şartı olmadan eşiği düşürmek yanlış alarma yol
açardı; süre şartı geldiği için eşik %30'dan %25'e çekilebildi — masadaki gerçek
vaka %31,9 sapma yapıyordu ve %30 eşiği kıl payı geçiyordu, %25 daha emniyetli. Uyarıdaki sayı da her µT oynamasında değil,
2 µT'yi aşan kaymalarda tazeleniyor. Durum satırı yazı gelip gidince ekranın
kaymaması için yerini her zaman ayırır (`minLines="2"`); aksi hâlde uyarı her
çıkışında kadran zıplıyordu.

## 5. Kıble, hedef kilidi, su terazisi, konum ve nokta

**Yönler (kıble, Mescid-i Aksa, Vatikan).** Konum bilinince kadranda yeşil
işaretler ve üst satırda dereceleri çıkar. Varsayılan olarak yalnızca kıble
açıktır; diğerleri ayarlardan açılır. Hesap, bulunduğunuz noktadan hedefe giden
büyük daire yayının çıkış açısıdır — kıblenin tanımı da budur, düz haritadaki
"sağ alt köşe" yönü değil:

```
θ = atan2( sin Δλ · cos φ₂ ,  cos φ₁ · sin φ₂ − sin φ₁ · cos φ₂ · cos Δλ )
```

Açı gerçek kuzeye göredir. İstanbul'dan ölçülen değerler: Kâbe 152,0°,
Mescid-i Aksa 150,1°, Vatikan 279,7°.

İlk ikisi arasında **yalnızca 1,9° var** ve bu, kadran yerleşiminin tamamını
belirledi. Kadranın kenarında altı ayrı işaret yarışıyor: **M** ve yer yazıları,
güneş diski, ay diski, nokta baklavası. Üç katmanlı çözüm:

1. **Birbirine 6°'den yakın yer işaretleri tek etikette birleşir** (`Kıble·Aksa`),
   konumları vektörel ortalamadan. O çözünürlükte ikisi zaten aynı yöndür; kesin
   dereceler alt satırda yazar.
2. Kalan işaretlerin hepsi — yazılar **ve** semboller — tek bir listede toplanıp
   **yarıçaplara dağıtılır**: 0,94R, 0,855R, 0,79R. Bir işaret, aynı yarıçapta
   açısal genişliklerinin toplamından yakın bir komşu bulursa bir alt kademeye
   iner. Geniş olanlar önce yerleşir, çünkü dar olanlar kalan boşluklara daha
   kolay sığar.
3. Her işaretin açısal genişliği kendi piksel genişliğinden hesaplanır
   (`atan(yarı_genişlik / yarıçap)`), yani uzun bir yazı kısa bir sembolden daha
   çok yer kaplar ve komşularını daha kolay aşağı iter.
4. Ekranın tepesindeki sabit gösterge de bu yarışa katılır: yerinden
   oynatılamadığı için dış halkaya önceden yerleştirilir ve yakınına düşen
   işaretler onun için de bir alt kademeye iner. Aksi hâlde telefonun baktığı
   yöne denk gelen etiket göstergenin altında kalıyordu.

Ekrandaki yazılar da anlamına göre ayrılmıştır: üst satır pusulanın kendi
durumudur (manyetik açı ve sapma), kadranın altındaki satır ise işaretlerin
yönleridir. Hepsi tek satıra dizilince üç sıraya taşıp okunmaz oluyordu; ayrıca
bunlar farklı sorular — "pusula ne diyor" ile "neyin nerede olduğu".

Bu sistem iki ayrı hatadan doğdu. Önce yalnızca yazılar dağıtılıyordu ama
kademeler 0,90 ve 0,845'ti; aradaki 0,055R yazı yüksekliğinden (0,10R) küçük
olduğu için etiketler farklı yarıçapta olmalarına rağmen yine biniyordu. Sonra
semboller hiç dağıtıma girmediği için ay ile kaydedilen nokta üst üste geldi.
Şimdi ikisi de aynı sistemden geçiyor.

**Hedef kilidi.** İki yolu var. Kadrana dokunmak o an baktığınız yönü kilitler;
alt satıra dokunmak ise açıyı **sayıyla girmenizi** sağlar — haritadan okunan bir
kerterizi takip etmek için gereken budur, çünkü dokunarak yalnızca hâlihazırda
baktığınız yön kilitlenebilir. Girilen değer ekrandaki çerçeve ve birimle aynıdır
(manyetik moddayken manyetik, mil seçiliyse mil). Her iki durumda da: sarı bir hat
kadranda o yönü işaretler, alt satır `Hedef 81° · 12° sağa` diye ne kadar
dönmeniz gerektiğini söyler, ±2° içinde `yön tutuyor` yazar. Tekrar dokunmak
bırakır. Kilit `SharedPreferences`'a yazıldığı için uygulamayı kapatıp açsanız
da durur.

Hedef **manyetik** çerçevede saklanır. Sebebi: konum izni sonradan verilirse
sapma devreye girer ve ekrandaki bütün açılar kayar; hedef manyetik olarak
tutulunca kilitlediğiniz fiziksel yön aynı kalır.

**Su terazisi.** Kadranın göbeğindeki kabarcık telefonun eğimini gösterir.
Gerçek terazideki gibi yukarıda kalan tarafa kaçar, ortalanınca telefon düzdür
ve kabarcık beyaza döner. Eğim, remap edilmiş dönüş matrisinin `[8]` elemanının
ark kosinüsüdür (ekran normalinin düşeyden açısı); 40°'yi geçince "telefonu
yatay tutun" uyarısı çıkar, 30°'nin altına inince kaybolur — sınırda titremesin
diye açma ve kapama eşikleri farklı.

**Konum paneli.** Konum bulununca kadranın altında koordinatlar, rakım ve hata
payı görünür:

```
40,98767° K  29,13664° D · 189 m · ±100 m
```

Satıra **dokunmak** biçimi derece-dakika-saniyeye çevirir
(`40°59'15,6" K  29°08'11,9" D`), tekrar dokunmak ondalığa döndürür. **Uzun
basmak** koordinatları panoya kopyalar; kopyalanan biçim haritalara yapıştırmaya
uygun olsun diye nokta ayraçlı ve işaretlidir (`40.987670, 29.136640`), ekranda
gösterilen biçimden bağımsızdır. Android 13'ten itibaren sistem kendi kopyalama
onayını gösterdiği için uygulama kendi bildirimini o sürümlerde çıkarmaz.

Koordinat paneli anlamlı olsun diye artık hassas konum da isteniyor. Kullanıcı
"yaklaşık"ı seçerse uygulama çalışmaya devam eder ve satırın sonuna *yaklaşık*
yazar — hata payı zaten kilometrelerce olur, bunu gizlemek yanıltıcı olurdu.
Sapma için ağ konumu yeterdi; panel için GPS'in hassasiyeti de gerektiğinden
artık iki sağlayıcı da dinlenir ve gelen fix'lerden en iyisi seçilir (bir
dakikadan yeni olan koşulsuz kazanır, eşit yaştakilerde hata payı küçük olan).

**Nokta kaydetme ve geri dönüş.** Kadrana **uzun basmak** bulunduğunuz yeri
kaydeder; kadranda mor bir **Nokta** işareti ve alt satırda yön ile mesafe belirir:

```
Nokta 265° · 17 m
```

Noktanın üstündeyken yön yazılmaz, `Nokta · buradasınız` denir. Sebebi: mesafe
konum hatasının altına inince yön anlamını yitirir — hata çemberinin içinde hangi
yöne bakacağınızı söylemek uydurma olur. Eşik fix'in kendi hata payıdır, ama
10-25 m aralığına sıkıştırılır: çok iyi bir fix'te bile birkaç metrede yön
güvenilmez, çok kötü bir fix'te de yüz metre öteye "buradasınız" demek yanlış
olurdu.

Tekrar uzun basmak siler. Nokta `SharedPreferences`'a yazıldığı için uygulamayı
kapatsanız da durur. Yön, kıbleyle aynı büyük daire formülünden; mesafe
`Location.distanceBetween` ile (WGS84 elipsoidi). Bir kilometrenin altında metre,
üstünde kilometre yazar.

Hedef ve nokta bilgisi tek satırda, kadrandaki işaretlerle aynı renklerde
gösterilir; ikisi de yokken satır iki hareketi birden anlatır. Satır iki sıra yer
kaplar (`minLines="2"`), yoksa nokta kaydedildiğinde ekran zıplardı.

Kadranda dört işaret var ve hiçbiri diğerine karışmasın diye hem renk hem şekil
ayrılmış: mavi **M** yazısı manyetik kuzey, yeşil **Kıble** yazısı Kâbe yönü,
altın **disk** güneş, mor **baklava** kaydedilen nokta. İki yazı farklı
yarıçaplara konur (`M` 0,90R, `Kıble` 0,845R). Üçüncü bir yazıya yer yok: yön
harfleri 0,62-0,785R bandını kaplıyor, üstte kalan şerit iki satır ancak alıyor —
bu yüzden güneş ve nokta yazı yerine şekille gösteriliyor, adları zaten
kendi renklerinde alt satırlarda geçiyor.

**Güneş azimutu.** Kadranda altın bir disk güneşin yönünü gösterir, üst satırda
derecesi yazar. Disk doluysa güneş ufkun üstünde, içi boşsa batmıştır — yön yine
bilgidir ama bakacak güneş yoktur.

Bunun asıl değeri çapraz kontrol: **güneşin yönü manyetik alandan tamamen
bağımsızdır.** Pusuladan şüphelenirseniz (yakında metal, kalibrasyon bozuk,
bozulma uyarısı çıkmış) güneşe bakıp kadranı sınayabilirsiniz. Bugünkü masa
vakasında olduğu gibi manyetik alan bozukken bu, yönü kurtaran tek referanstır.

Hesap NOAA'nın güneş konumu algoritmasıdır (`Sun.kt`, saf Kotlin, ~70 satır) ve
tamamen UTC üzerinden yürür: gerçek güneş saati, boylamın her derecesi 4 dakika
sayılarak ve zaman denklemi eklenerek doğrudan UTC'den kurulur. Bu yüzden cihazın
**saat dilimi ayarının yanlış olması sonucu etkilemez**; yalnızca saatin kendisi
doğru olmalıdır. Güneş dakikada 0,25° yol aldığı için konum dakikada bir tazelenir.

**Güneşin yolu.** Kadranın kenarındaki altın yay, güneşin bugün doğduğu yönden
battığı yöne, güneyin üzerinden uzanır; uçlarındaki çentikler doğuş ve batış
noktalarıdır ve dereceleri alt satırda yazar. Güneş diski bu yayın üzerinde
ilerler.

"Güneş doğudan doğar" yalnızca ekinokslarda doğrudur. İstanbul'da doğuş noktası
yıl boyunca **64°'lik bir yay** tarar:

| Tarih | Sapma | Doğuş | Batış |
|---|---|---|---|
| 21 Haziran | +23,4° | 57,3° | 302,7° |
| 21 Mart / 23 Eylül | 0° | 89,3° | 270,7° |
| 21 Aralık | −23,4° | 121,0° | 239,0° |

Ekinoksta bile tam 90° değil 89,3°, çünkü güneş merkezi ufkun 0,833° altındayken
görünür: atmosferik kırılma 34′ yukarı kaldırır, güneşin yarıçapı 16′ ekler. Bu
yayın genişliği enlemle büyür — ekvatorda 47°, İstanbul'da 64°, 60°K'de 106°,
kutup dairesinde 161°. Kutup gündüzü ya da gecesinde güneş ufku hiç kesmez;
formülün kosinüsü ±1'i aştığı için yay o günlerde çizilmez.

Yay bilinçli olarak **etiketsiz**: tek bir öğe hem iki uç noktayı hem de yolu
anlatıyor ve kadranın yazı bütçesini harcamıyor.

Doğrulama: uygulama 162° gösterirken bağımsız bir formülasyon (Astronomical
Almanac, saat açısını zaman denklemi yerine GMST'den kuran yol) 162,0° verdi.
Yan kontroller de tuttu — 23 Ağustos için deklinasyon 11,37° (beklenen ~11,5°),
o gün İstanbul'da güneşin azami yüksekliği 60,4°.

**Ana yönlerde titreşim.** Gösterilen açı K/D/G/B'den birine 2° yaklaşınca kısa
bir tık verilir; 5° uzaklaşana kadar yeniden tetiklenmez. Ekrana bakmadan yön
tutmaya yarar. Uygulama açılırken ana yöne bakıyorsanız titremez — ilk okuma
yalnızca başlangıç bölgesini kaydeder.

Titreşim `performHapticFeedback` ile değil, doğrudan `Vibrator` ile verilir
(45 ms, tam genlik). Karşılığında `VIBRATE` izni gerekir (normal izin, çalışma
anında sorulmaz) ve kullanıcının sistem dokunsal ayarına elle bakılır: kapalıysa
titremez.

Bu iki sayı deneyle bulundu ve ikisi de aynı fiziksel sebebe çıkıyor. Önce
`performHapticFeedback` sabitleri denendi: Galaxy A51 / Android 13'te
`CLOCK_TICK`, `KEYBOARD_TAP`, `VIRTUAL_KEY`, `CONFIRM` ve `CONTEXT_CLICK`
**hepsi `true` dönüyor ama hiçbiri titremiyor**; çalışan tek sabit 48 ms'lik
`LONG_PRESS`. Yani dönüş değeri bu cihazda hiçbir şey ifade etmiyor.

"48 ms fazla sert" diye kendi efektimiz 20 ms verildiğinde `dumpsys
vibrator_manager` kaydı düzgün düşüyordu ama elde hiçbir şey hissedilmiyordu.
Sebep: A51'in motoru **ERM** (dönen ağırlık) ve dönmeye başlaması 30-50 ms
alıyor — 20 ms'lik darbe motoru hızlandırmaya yetmiyor. `LONG_PRESS`'in tek
çalışan sabit olması da tesadüf değilmiş, eşik tam orada. Cihazın dokunsal
şiddeti `TOUCH=(LOW)` olduğu için varsayılan genlik ayrıca kısılıyordu, o yüzden
genlik açıkça 255 veriliyor.

Buradan çıkan genel ders: titreşim kaydının `dumpsys`'te görünmesi hissedildiği
anlamına gelmiyor, `performHapticFeedback`'in `true` dönmesi de öyle. İkisi de
bu cihazda sessizce yalan söyledi; tek geçerli doğrulama telefonu eline alıp
denemek oldu.

Art arda tetiklemeye karşı 700 ms'lik asgari aralık var. Bu da ölçümden geldi:
açılışta yumuşatma otururken açı birkaç bölgeyi hızla kesip 23 ms içinde üç tık
üretmişti.

**Gece modu.** Büyük derece yazısına dokunmak ekranı gece moduna alır: zemin tam
siyah, her şey kırmızı. Tekrar dokunmak geri döndürür, seçim kalıcıdır.

Sebebi göz fizyolojisi: karanlığa uyum sağlamış göz kırmızı ışıktan neredeyse hiç
etkilenmez, ama mavi-yeşil ışık uyumu saniyeler içinde bozar ve yeniden karanlığa
alışmak yarım saat sürer. Gece yön bulurken ekrana her bakışta gece görüşünü
baştan kaybetmemek için kadran tümüyle kırmızıya çevrilir; zemin de tam siyah
olur, ekran ne kadar az ışık verirse o kadar iyi.

Bedeli şu: gece modunda işaretler **renkle ayırt edilemez**, çünkü hepsi aynı
tonun farklı parlaklıklarıdır. Bu yüzden şekil ayrımı burada işe yarıyor — güneş
disk, nokta baklava, manyetik kuzey ile kıble ise yazı. Renk düzeni tek yerde
(`Palette.kt`) tanımlıdır ve iki hâli vardır; hem kadran hem yazılar aynı
paletten beslenir, o yüzden geçiş tek satırdır.

**Ay.** Kadranda yönü, evresi diskin doluluğuyla çizilir; ufkun altındaysa soluk
kalır. Gece modunun tam tamamlayıcısıdır: ay çıkmışsa gece yön bulmanın en
pratik referansıdır ve güneş gibi manyetik alandan bağımsızdır.

Hesap güneşten belirgin biçimde zordur. Güneşin yörüngesi tek bir elipsle iyi
yaklaşılırken ay, Dünya ile Güneş arasında sürekli çekiştiği için düzensiz
hareket eder; bozulma (perturbation) terimleri olmadan hata 2°'ye kadar çıkar.
En büyük ikisi evection (1,274°) ve variation (0,658°) terimleridir. Ayrıca ayın
paralaksı ihmal edilemez (~1°), çünkü ay yakındır ve gözlemci Dünya'nın
merkezinde değil yüzeyindedir — yükseklik buna göre düzeltilir, yoksa ufka yakın
ayın "doğdu mu battı mı" kararı yanlış çıkar.

Doğrulama fiziksel sabitlerle yapıldı: yıldızıl ay 27,33 gün (olması gereken
27,32), ekliptik enlem en çok 5,29° (sınır 5,3), uzaklık 57,0-63,6 Dünya yarıçapı
(55,9-63,8), deklinasyon en çok 28,4° (sınır 28,7). Bilinen bir yeni ay anında
(JD 2451550,09766) uzanım 358,2°, aydınlık %0,02.

İlk denemede epok yanlıştı: Schlyter'in gün sayısı 2000 Ocak 0,0'dan, yani
31 Aralık 1999 00:00 UT'den (JD 2451543,5) başlar. JD 2451545,0 (1 Ocak öğlen)
kullanmak 1,5 günlük kayma, o da ayın yerinde 18° hata demekti — yeni ay testi
bunu ilk denemede yakaladı.

Ay simgesi, kadranın diğer işaretlerinin aksine **kadranla birlikte
döndürülmez**. Evre şekli yönlü bir simgedir; işaret kadranın dibine düştüğünde
180° dönüp aynalanıyor ve büyüyen ay küçülen gibi görünüyordu. Konumu açıdan
hesaplanıp simge ekrana dik çiziliyor.

## 6. Ayarlar

Sağ üstteki dişliden açılır. Dış bağımlılık olmadığı için `PreferenceFragment`
yok; arayüz `SettingsActivity` içinde kodla kuruluyor ve aynı paleti kullanıyor,
yani gece modunda ayarlar ekranı da kırmızıya dönüyor.

| Ayar | Ne yapar |
|---|---|
| **Gece modu** | Siyah zemin, kırmızı kadran. Büyük derece yazısına dokunmak da aynı işi yapar. |
| **Açı birimi** | Derece (0-360) ya da NATO mili (0-6400). Bütün yön yazılarını etkiler; sapma derecede kalır, çünkü konumun fiziksel özelliğidir. |
| **Ekranı açık tut** | `FLAG_KEEP_SCREEN_ON`. Kapatılabilir olması pil için önemli. |
| **Tam ekran** | Durum ve gezinme çubuklarını gizler; kenardan kaydırınca geçici olarak geri gelirler. Kazanılan yer doğrudan kadranın çapına gider. |
| **Gerçek kuzeyi kullan** | Kapatılırsa kadran manyetik kuzeye oturur. |
| **Yumuşatma** | Sakin (0,06) / Dengeli (0,12) / Çevik (0,25). Ortadaki, uygulamanın başından beri kullandığı değer. |
| **Ana yönlerde titreşim** | Tıkı tümüyle kapatır. |
| **Kadran işaretleri** | Manyetik kuzey (M), su terazisi, güneş, güneşin yolu, ay ve yön noktalarını (Kâbe, Mescid-i Aksa, Vatikan) ayrı ayrı açar/kapatır. |

Hedef ve nokta işaretleri o listede yok, çünkü zaten kadrana dokunarak ya da uzun
basarak açılıp kapanıyorlar; ayrıca bir anahtar koymak "kilitli ama görünmez
hedef" gibi kafa karıştırıcı bir durum üretirdi.

Kuzey türü ayarı göründüğünden daha derin: kıble, güneş ve nokta **gerçek kuzeye
göre** hesaplanır, kadran ise manyetik kuzeye oturmuş olabilir. Bu yüzden her
işaret çizilmeden önce `toDialFrame()` ile kadranın çerçevesine çevrilir — manyetik
moddayken sapma kadar geri alınır. Manyetik moddayken kadranın "M" işareti de
gizlenir, çünkü kuzeyle çakışır ve bilgi vermez.

Ayarlar ile ana ekran aynı `SharedPreferences` dosyasını paylaşır; anahtarlar
`Prefs.kt`'te toplanmıştır. Ana ekran `onResume`'da hepsini yeniden okuyup
uyguladığı için ayrı bir "kaydet" adımı yoktur.

## 7. Kodun yapısı

Uygulama hem dikey hem yatay çalışır. `remapCoordinateSystem` zaten sensör
eksenlerini ekran yönüne göre eşlediği için okuma her iki yönde de **ekranın üst
kenarının** baktığı yönü verir; telefonu yan çevirince açı 90° kayar, çünkü artık
farklı bir kenar öne bakmaktadır. Yatay yerleşim ayrı bir dosyada (`layout-land/`),
kimlikler aynı olduğu için kod değişmez.

| Dosya | İş |
|---|---|
| `app/src/main/java/com/cem/pusula/MainActivity.kt` | Sensör okuma, açı hesabı, yumuşatma, konum/sapma/kıble, hedef kilidi |
| `app/src/main/java/com/cem/pusula/CompassView.kt` | Kadranın `Canvas` ile çizimi: ibre, işaretler, su terazisi |
| `app/src/main/java/com/cem/pusula/Sun.kt` | Güneşin azimut, yükseklik, doğuş ve batış yönleri (NOAA) |
| `app/src/main/java/com/cem/pusula/Moon.kt` | Ayın azimut, yükseklik ve evresi (Schlyter) |
| `app/src/main/java/com/cem/pusula/Places.kt` | Kâbe, Mescid-i Aksa, Vatikan koordinatları |
| `app/src/main/java/com/cem/pusula/Palette.kt` | Gündüz ve gece renk düzenleri |
| `app/src/main/java/com/cem/pusula/Prefs.kt` | Ayar anahtarları ve varsayılanları |
| `app/src/main/java/com/cem/pusula/SettingsActivity.kt` | Ayarlar ekranı (kodla kurulan arayüz) |
| `app/src/main/res/layout/activity_main.xml` | Dikey yerleşim: yazılar üstte, kadran altta |
| `app/src/main/res/layout-land/activity_main.xml` | Yatay yerleşim: yazılar solda, kadran sağda |

Nasıl çalışıyor:

- Öncelik `TYPE_ROTATION_VECTOR` sensöründe; cihazda yoksa ivmeölçer + manyetometre
  ikilisine düşülür (`getRotationMatrix`).
- `remapCoordinateSystem` ile sensör eksenleri ekran yönüne göre eşlenir.
- Açı doğrudan değil, `sin`/`cos` bileşenleri üzerinden yumuşatılır — aksi hâlde
  359° → 0° geçişinde ibre bir tam tur atardı. Yumuşatma katsayısı `alpha = 0.12f`;
  daha çevik istiyorsanız büyütün, daha sakin istiyorsanız küçültün.
- Aynı `getOrientation` çağrısının `[1]` ve `[2]` değerleri (pitch/roll) su
  terazisini besler; onlar da aynı katsayıyla yumuşatılır.
- Sensör hassasiyeti düştüğünde ekranda kalibrasyon uyarısı çıkar (telefonu havada
  8 çizer gibi hareket ettirmek düzeltir). Kalibrasyon uyarısı, eğim uyarısından
  önceliklidir.
- Durum satırında öncelik sırası: manyetik bozulma > kalibrasyon > eğim. Bozulma
  en tehlikelisidir, çünkü diğer ikisinin aksine hiçbir görsel ipucu vermez.
- Kadrandaki işaret renkleri tek yerde (`CompassView.Companion`) tanımlıdır;
  ekrandaki yazılar da aynı renkleri kullanır, böylece hangi satırın hangi
  işarete ait olduğu bakınca anlaşılır.

## 8. Erişilebilirlik

**Kontrast.** Bütün yazı renkleri kendi zeminine karşı en az **4,5:1** verir (WCAG
AA); 52sp'lik derece yazısı için eşik 3:1'dir ve 18,5:1 ile fazlasıyla aşar.
Grafik öğeler (ibrenin güney yarısı gibi) 3:1 eşiğine tabidir.

Ölçüm beş eksik buldu: gündüz paletinde ipucu yazısı 4,0:1, gece paletinde ise
soluk yazı 4,1, ipucu 2,7, kıble 3,4 ve nokta 2,9. Gece paletini düzeltmek
tasarımı değiştirmeyi gerektirdi: renkleri eşiğe kadar aydınlatınca **parlaklık
kademeleriyle kurulan ayrım kayboluyordu**, hepsi aynı kırmızıya yapışıyordu.
Çözüm ayrımı parlaklıktan **tona** taşımak oldu — kıble turuncuya (20°), nokta
mora (352°), manyetik kuzey kehribara kaydı. Hepsi hâlâ kırmızı ailesinde, yani
gece görüşü korunuyor, ama artık hem okunuyorlar hem birbirinden ayrılıyorlar.

**Ekran okuyucu.** TalkBack ham metni yanlış okuyordu: "284°" derece işaretini her
zaman söylemiyor, "BKB" ise harf harf okunuyordu. Artık açıklamalar açık yazılıyor:

```
285°                          -> "285 derece, batı kuzeybatı, Gerçek kuzey"
Manyetik 278° · Sapma 6,4°D   -> "Manyetik 278 derece, sapma 6,4 derece doğuya"
```

Yön kısaltması satırı erişilebilirlik ağacından çıkarıldı, çünkü aynı bilgi
derece yazısının açıklamasında zaten var; iki kez okunması gereksiz gürültüydü.

Kadranın iki hareketi de adıyla bildiriliyor: varsayılan "etkinleştir" ve "uzun
bas" etiketleri ne yaptıklarını söylemediği için `onInitializeAccessibilityNodeInfo`
ile "yönü kilitle" ve "nokta kaydet" olarak adlandırıldılar.

Uyarı satırı **canlı bölge** (`accessibilityLiveRegion="polite"`): manyetik
bozulma, kalibrasyon ve eğim uyarıları belirdiklerinde kendiliğinden okunur.
Uyarılar seyrek olduğu için bu gürültü yaratmaz — ama açı için aynısını yapmak
saniyede birkaç kez konuşmak olurdu, o yüzden açı yalnızca odaklanınca okunur.

Ayarlarda her satır tek odak durağıdır. Önce üç duraktı (başlık, açıklama,
anahtar); artık yazılar anahtarın açıklamasına taşınıyor ve satır durumuyla
birlikte okunuyor: *"Gece modu. Siyah zemin, kırmızı kadran — gece görüşünü korur.
Kapalı."*

**Dokunma hedefleri** en az 48dp: dişli düğmesi ve ayar satırları buna göre
büyütüldü.

## 9. Diller

Uygulama altı dilde: **İngilizce, Türkçe, Fransızca, Almanca, İtalyanca,
İspanyolca**. Uygulama içinde dil ayarı yoktur — sistemde hangisi seçiliyse o
kullanılır. Android 13'ten itibaren `locales_config.xml` sayesinde
`Ayarlar → Uygulamalar → Pusula → Dil` altında uygulamaya özel bir seçici de
çıkar.

Varsayılan (`values/`) **İngilizce**, Türkçe ise `values-tr/` altındadır. Sebep:
listede olmayan bir dil seçildiğinde (Japonca, Arapça…) uygulama varsayılana
düşer; orada Türkçe olsaydı o kullanıcılar okuyamadıkları bir dille karşılaşırdı.

Çeviri yalnızca cümleleri değil, **yön sisteminin kendisini** kapsar. Kısaltmalar
dilden dile değişir ve bunlar kadranın üstünde de yazılıdır:

| | K/N | Doğu | Batı | Kuzeydoğu |
|---|---|---|---|---|
| Türkçe | K | **D** | **B** | KD |
| İngilizce | N | E | W | NE |
| Fransızca | N | E | **O** (ouest) | NE |
| Almanca | N | **O** (Ost) | W | **NO** |
| İtalyanca / İspanyolca | N | E | **O** | NE |

Almanca'da doğu **O**, Fransızca'da batı **O** — aynı harf iki dilde zıt yönü
gösterir. Bu yüzden yön harfleri kodda gömülü olamazdı; hepsi (kadran harfleri,
16 kısaltma, koordinatların yarım küre harfleri, sapmanın yön eki) kaynak
dosyalara taşındı. Ondalık ayracı da dile uyar: aynı sapma İngilizce'de
`6.4°E`, Almanca'da `6,4°O` yazar.

Ekran okuyucu için kullanılan açık yön adları da her dilde ayrıdır
(`kuzey kuzeydoğu` / `north-northeast` / `Nordnordost`).

## 10. Sorun giderme

### "Kuruldu" dedi ama uygulama listede yok

Önce hangi durumda olduğunuzu ayırın:

`Ayarlar → Uygulamalar → Tüm uygulamaları göster` listesinde **Pusula** var mı?

- **Varsa:** uygulama kurulu, sorun launcher'da. Aynı ekrandaki **Aç** düğmesiyle
  hemen çalıştırabilirsiniz. Çekmecede görünmesi için: ana ekranı kapatıp açın
  (ya da telefonu yeniden başlatın) ve launcher'ın **gizli uygulamalar**
  ayarına bakın (Samsung: `Ana ekran ayarları → Uygulamaları gizle`,
  Xiaomi: `Ayarlar → Uygulamalar → Uygulama kilidi → Gizli uygulamalar`).
  Çekmece alfabetikse **P** harfinde arayın, ya da çekmecenin arama kutusuna
  "Pusula" yazın.
- **Yoksa:** kurulum aslında tamamlanmamış. Genelde Play Protect sessizce
  engellemiştir: `Play Store → profil simgesi → Play Protect → Ayarlar →
  Uygulamaları Play Protect ile tara` seçeneğini geçici olarak kapatıp APK'ya
  tekrar dokunun, kurulumdan sonra geri açın. Kurulum ekranında **Yükle**
  düğmesine bastığınızdan ve "Uygulama yüklendi" yazısını gördüğünüzden emin olun.

v1.1'den itibaren ikon her yoğunluk için PNG olarak paketleniyor ve activity'nin
kendi `label`/`icon` değerleri var — v1.0'da launcher'ın ikonu çözemeyip uygulamayı
çekmecede göstermemesi mümkündü.

### "Uygulama yüklenmedi" hatası

Sırasıyla şunlara bakın:

1. **Önce eski sürümü kaldırın.** En sık sebep budur. `Ayarlar → Uygulamalar →
   Tüm uygulamaları göster → Pusula → Kaldır`. Aynı paket adına (`com.cem.pusula`)
   sahip, farklı bir anahtarla imzalanmış bir kurulum varsa Android yeni APK'yı
   "Uygulama yüklenmedi" diyerek reddeder. **Debug APK ile release APK'nın
   imzaları farklıdır**, dolayısıyla debug'dan release'e geçerken kaldırma adımı
   zorunludur. Listede görünmüyorsa yarım kalmış bir kurulum kalmış olabilir;
   release APK'yı denemek çoğu zaman bunu da aşar.
2. **Release APK'yı kullanın.** `dist/` içindeki release APK hata ayıklama bayrağı
   taşımaz ve v1+v2+v3 şemalarının üçüyle de imzalıdır. Bazı OEM ROM'ları
   (özellikle MIUI/EMUI) `debuggable=true` işaretli APK'ları kurmayı reddeder.
3. **Dosya bozulmuş olabilir.** Telefondaki APK'nın boyutunu kontrol edin;
   release APK tam olarak **853.595 bayt** (~833 KB) olmalı. WhatsApp/Telegram
   gibi kanallar dosyayı bozabilir — Drive, e-posta eki veya USB tercih edin.
4. **Play Protect.** `Play Store → profil → Play Protect → Ayarlar` altından
   taramayı geçici kapatın, kurun, sonra geri açın.
5. **Depolama alanı.** 100 MB'ın altına düşmüş bir cihazda kurulum sessizce
   başarısız olur.

### Doğrulanmış kurulum (Samsung Galaxy A51, Android 13)

Uygulama SM-A515F / Android 13 (API 33, arm64-v8a) üzerinde kablosuz adb ile
kurulup çalıştırıldı: kurulum `Success`, uygulama çekmecede ikonuyla görünüyor,
sensör okuması doğru, logcat'te çökme yok.

Aynı cihazda **dosyaya dokunarak kurmak "Uygulama yüklenmedi" veriyordu**, oysa
telefondaki APK dosyasının sha256'sı kaynaktakiyle birebir aynıydı — yani dosya
bozuk değildi, engel cihazın kurulum yolundaydı (Play Protect taraması ve/veya
dosya yöneticisine verilmemiş "bilinmeyen uygulamaları yükle" izni). Samsung
cihazlarda en güvenilir yol adb ile kurmaktır:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
$ANDROID_HOME/platform-tools/adb install -r dist/pusula-1.4-release.apk
```

Kablosuz adb'de eşleştirme portu ile bağlantı portunun farklı olduğunu unutmayın;
eşleştirdikten sonra bağlantı portu `adb mdns services` ile bulunur. Telefon ile
bilgisayarın **aynı alt ağda** olması şart (192.168.1.x ile 192.168.2.x arasında
yönlendirme yoktur).

### Geliştirici seçenekleri ayarlarda görünmüyor

Bu menü varsayılan olarak gizlidir; yapı numarasına 7 kez dokununca ortaya çıkar.
Yolu markaya göre değişir:

| Marka | Yol |
|---|---|
| Pixel / stok Android | `Ayarlar → Telefon hakkında → Yapı numarası` |
| Samsung | `Ayarlar → Telefon hakkında → Yazılım bilgileri → Derleme numarası` |
| Xiaomi / Redmi (MIUI) | `Ayarlar → Telefon hakkında → MIUI sürümü` |
| Oppo / Realme | `Ayarlar → Cihaz hakkında → Sürüm → Derleme numarası` |
| Huawei | `Ayarlar → Telefon hakkında → Derleme numarası` |

7 kez dokunduktan sonra ekran kilidi PIN'inizi ister, sonra "Artık
geliştiricisiniz" mesajı çıkar. Menü şurada belirir:
`Ayarlar → Sistem → Geliştirici seçenekleri` (MIUI'de `Ayarlar → Ek ayarlar →
Geliştirici seçenekleri`). USB hata ayıklamayı oradan açarsınız.

adb'yi hiç kullanmak istemiyorsanız gerek de yok — 1. yöntem (dosyaya dokunup
kurmak) tek başına yeterlidir.
