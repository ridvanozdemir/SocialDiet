# SocialDiet — Google Play Release Checklist

Son güncelleme: 25 Ağustos 2026

## Hesap ve Play Console

- [ ] Geliştirici kimliği Google tarafından onaylandı
- [ ] İletişim telefonu doğrulandı
- [ ] Android cihaz doğrulaması tamamlandı
- [ ] SocialDiet Play Console'da oluşturuldu
- [ ] Uygulama dili Türkçe olarak ayarlandı
- [ ] Ücretsiz uygulama olarak ayarlandı

## Teknik yayın paketi

- [x] Paket adı: `com.ridvanozdemir.socialdiet`
- [x] `targetSdk = 36`
- [x] `compileSdk = 36`
- [ ] Kararlı release upload key oluşturuldu ve güvenli yedeklendi
- [ ] Keystore ve parolalar GitHub Secrets içine eklendi; repoya commit edilmedi
- [ ] GitHub Actions `bundleRelease` ile imzalı `.aab` üretiyor
- [ ] Play App Signing etkinleştirildi
- [ ] İlk Play AAB için versionCode/versionName kesinleştirildi

## Ürün işlevleri

- [x] Kayıt / giriş
- [x] Profil ve kilo hedefi
- [x] Fotoğraf seçme / kameradan çekme
- [x] Cihaz üzerinde AI besin tahmini
- [x] Kullanıcının AI tahminini düzeltmesi
- [x] Öğün kaydı
- [ ] Arkadaş arama / istek / kabul / silme
- [ ] Günlük kalori toplamı
- [ ] Arkadaşlara izin verilen ilerleme görünümü
- [ ] Günlük / haftalık hedefe uyum sıralaması
- [ ] Kullanıcı engelleme ve gerekiyorsa bildirme akışı
- [ ] Uygulama içi hesap silme
- [ ] Kullanıcıya ait Firestore verilerinin hesap silmeyle temizlenmesi

## Politika ve gizlilik

- [ ] Gizlilik politikası herkese açık HTTPS URL'de yayınlandı
- [ ] Gizlilik politikası uygulama içinden erişilebilir
- [ ] Harici hesap silme web sayfası yayınlandı
- [ ] Data Safety formu son release build ve Firebase SDK'larıyla doğrulandı
- [ ] Health Apps Declaration: Nutrition and Weight Management
- [ ] İçerik derecelendirme anketi tamamlandı
- [ ] Hedef kitle seçimi tamamlandı (öneri: 18+)
- [ ] App Access bölümüne Google incelemesi için çalışan test hesabı eklendi
- [ ] AI kalori tahmininin yaklaşık olduğu uygulamada ve mağaza metninde açıkça belirtiliyor

## Mağaza varlıkları

- [ ] 512 × 512 PNG Play Store simgesi
- [ ] 1024 × 500 PNG/JPEG feature graphic
- [ ] En az 2 telefon ekran görüntüsü; öneri 5–8 adet
- [ ] Ekran görüntüleri gerçek son sürümü gösteriyor
- [ ] Kısa açıklama (≤80 karakter)
- [ ] Tam açıklama (≤4000 karakter)
- [ ] Destek e-postası: `ridvanozdemir.dev@gmail.com`

### Önerilen screenshot sırası

1. Giriş / kayıt veya SocialDiet ana ekranı
2. Profil ve kilo hedefi
3. Öğün fotoğrafı seçme
4. AI analiz sonucu ve manuel düzeltme
5. Günlük kalori özeti
6. Arkadaşlar
7. Günlük / haftalık lig
8. Hedef tamamlama ekranı

## Test ve yayın

- [ ] Internal Testing AAB yüklendi ve kendi cihazında Play üzerinden kuruldu
- [ ] Crash / temel akış testi yapıldı
- [ ] Closed Testing tester listesi hazırlandı
- [ ] En az 12 tester teste katıldı ve 14 gün kesintisiz opted-in kaldı
- [ ] Tester geri bildirimleri kaydedildi ve anlamlı düzeltmeler yapıldı
- [ ] Production access başvurusu tamamlandı
- [ ] Production release review'a gönderildi

## Yayından hemen önce son kontrol

Store listing yalnızca gerçekten çalışan özellikleri anlatmalı. Üretim tarihinde tamamlanmamış sosyal özellikler açıklamadan ve ekran görüntülerinden çıkarılmalıdır.
