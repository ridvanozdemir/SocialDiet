# SocialDiet — Google Play Release Checklist

Son güncelleme: 31 Ağustos 2026

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
- [ ] Google Authentication sağlayıcısı Firebase Console'da etkinleştirildi
- [ ] Android signing SHA-1 Firebase projesine eklendi
- [ ] OAuth client içeren güncel `google-services.json` projeye eklendi
- [ ] Güncel `firestore.rules` Firebase Console'a yayınlandı
- [ ] Kararlı release upload key oluşturuldu ve güvenli yedeklendi
- [ ] Keystore ve parolalar GitHub Secrets içine eklendi; repoya commit edilmedi
- [ ] GitHub Actions `bundleRelease` ile imzalı `.aab` üretiyor
- [ ] Play App Signing etkinleştirildi
- [ ] İlk Play AAB için versionCode/versionName kesinleştirildi

## Ürün işlevleri

- [x] Kayıt / giriş
- [x] Şifremi unuttum / şifre sıfırlama e-postası
- [x] E-posta doğrulama ve yeniden gönderme akışı
- [x] Google ile giriş uygulama kodu / Credential Manager akışı
- [x] Şifre değiştirme ve yeniden doğrulama
- [x] Profil ve kilo hedefi
- [x] Fotoğraf seçme / kameradan çekme
- [x] Cihaz üzerinde AI besin tahmini
- [x] Kullanıcının AI tahminini düzeltmesi
- [x] Öğün kaydı
- [x] Gerçek günlük kalori toplamı ve öğün kırılımı
- [x] Kullanıcı adıyla kullanıcı arama
- [x] Arkadaşlık isteği gönderme
- [x] Arkadaşlık isteği kabul / reddet
- [x] Arkadaş silme
- [x] Arkadaşlara izin verilen ilerleme profilini görüntüleme
- [x] Günlük / haftalık hedefe uyum sıralaması
- [x] Uygulama içi hesap silme
- [x] Kullanıcıya ait bilinen Firestore verilerinin hesap silmeyle temizlenmesi
- [ ] Kullanıcı engelleme ve gerekiyorsa bildirme akışı

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

- [ ] Debug/CI derlemesi temiz
- [ ] Internal Testing AAB yüklendi ve kendi cihazında Play üzerinden kuruldu
- [ ] Crash / temel akış testi yapıldı
- [ ] Kayıt > e-posta doğrulama > profil > öğün > Bugün temel akışı test edildi
- [ ] İki ayrı hesapla arkadaşlık isteği / kabul / silme test edildi
- [ ] Lig skorunun iki ayrı hesapla doğruluğu test edildi
- [ ] Şifremi unuttum ve şifre değiştirme gerçek e-posta hesabıyla test edildi
- [ ] Hesap silme sonrası Auth + Firestore veri temizliği doğrulandı
- [ ] Google ile giriş release signing yapılandırmasıyla test edildi
- [ ] Closed Testing tester listesi hazırlandı
- [ ] Kapalı test için geçerli Google Play tester/opt-in gereksinimleri yayın öncesi güncel dokümantasyondan doğrulandı
- [ ] Tester geri bildirimleri kaydedildi ve anlamlı düzeltmeler yapıldı
- [ ] Production access başvurusu tamamlandı
- [ ] Production release review'a gönderildi

## Yayından hemen önce son kontrol

Store listing yalnızca gerçekten çalışan ve son build üzerinde test edilmiş özellikleri anlatmalı. Firebase Console yapılandırması, Firestore Rules yayını ve gerçek cihaz testleri tamamlanmadan sosyal/Google giriş özellikleri üretim hazır kabul edilmemelidir.
