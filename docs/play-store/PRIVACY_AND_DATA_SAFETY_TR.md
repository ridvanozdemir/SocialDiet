# SocialDiet — Gizlilik Politikası ve Data Safety Taslağı

Son güncelleme: 25 Ağustos 2026

> Bu belge üretim öncesi taslaktır. Google Play'e gönderilmeden önce uygulamanın son sürümündeki gerçek veri akışlarıyla birebir doğrulanmalıdır.

## Gizlilik Politikası Taslağı

SocialDiet, kullanıcıların kilo hedeflerini, günlük kalori hedeflerini ve öğün kayıtlarını takip etmelerini sağlayan bir Android uygulamasıdır. Geliştirici iletişim adresi: `ridvanozdemir.dev@gmail.com`.

### Topladığımız ve işlediğimiz bilgiler

SocialDiet hesap ve uygulama özelliklerini sunabilmek için aşağıdaki bilgileri işleyebilir:

- E-posta adresi
- Firebase kullanıcı kimliği
- Kullanıcı adı ve görünen ad
- Boy bilgisi
- Başlangıç, güncel ve hedef kilo
- Günlük kalori hedefi
- Öğün türü, kalori, porsiyon ve makro besin kayıtları
- Kilo güncelleme geçmişi
- Arkadaşlık ilişkileri ve sosyal özelliklere ait durum bilgileri, bu özellikler üretim sürümünde etkinse

Firebase Authentication kayıt ve giriş sırasında IP adresi ve teknik kullanıcı aracısı bilgileri gibi bazı teknik verileri güvenlik ve kötüye kullanımı önleme amaçlarıyla otomatik olarak işleyebilir.

### Öğün fotoğrafları ve yapay zekâ

Mevcut V0.3 tasarımında öğün fotoğrafı cihaz üzerinde çalışan yapay zekâ modeliyle analiz edilir. Fotoğraf AI analizi için harici bir yapay zekâ servisine gönderilmez ve Firebase Storage'a yüklenmez. Kullanıcı fotoğrafı kaldırdığında uygulama bu fotoğrafın uzak bir kopyasını saklamaz.

Bu davranış gelecekte değişirse gizlilik politikası ve Google Play Data Safety beyanı yayınlanmadan önce güncellenecektir.

### Verileri neden kullanıyoruz?

Veriler aşağıdaki amaçlarla kullanılır:

- Hesap oluşturma ve kimlik doğrulama
- Kullanıcı profilini ve kilo hedefini saklama
- Öğün ve kalori takibini sağlama
- İlerleme ve hedef tamamlama özelliklerini sunma
- Arkadaşlık ve sıralama özelliklerini sunma, etkin olduğu durumda
- Güvenlik, kötüye kullanımın önlenmesi ve hizmetin çalışmasını sağlama

### Üçüncü taraf hizmetler

SocialDiet aşağıdaki Google Firebase hizmetlerini kullanır:

- Firebase Authentication
- Cloud Firestore

Bu hizmetler uygulamanın kimlik doğrulama ve veri saklama altyapısını sağlar. Kullanıcı verileri satılmaz.

### Veri güvenliği

Firebase üzerinden iletilen veriler HTTPS kullanılarak aktarım sırasında şifrelenir. Uygulama verilerine erişim Firebase Authentication ve Firestore Security Rules ile sınırlandırılır.

### Veri saklama ve silme

Kullanıcı verileri hesabın ve ilgili uygulama özelliklerinin çalışması için gerekli olduğu sürece saklanır.

**Üretim öncesi zorunlu iş:** SocialDiet'e uygulama içinden hesap silme akışı ve herkese açık bir web hesabı silme talep sayfası eklenecektir. Hesap silme işlemi, Firebase Authentication hesabı ile birlikte kullanıcıya ait Firestore kayıtlarını da silmelidir. Yasal olarak saklanması gereken bir veri bulunursa kullanıcıya açıkça bildirilecektir.

### Çocukların gizliliği

SocialDiet çocuklara yönelik olarak tasarlanmamıştır. Üretim sürümü için hedef kitle 18 yaş ve üzeri olarak planlanmaktadır.

### Değişiklikler

Bu politika uygulamanın işlevleri veya veri işleme yöntemleri değiştiğinde güncellenebilir. Güncel sürümde son güncelleme tarihi belirtilir.

### İletişim

Gizlilik ile ilgili sorular için: `ridvanozdemir.dev@gmail.com`

---

## Google Play Data Safety Taslağı

### Genel

- Uygulama veri topluyor mu? **Evet**
- Veriler üçüncü taraflarla reklam/bağımsız ticari amaçla paylaşılıyor mu? **Hayır**
- Veriler aktarım sırasında şifreleniyor mu? **Evet**
- Kullanıcı veri silme talebinde bulunabilecek mi? **Evet — üretim öncesi hesap silme akışı tamamlanmalı**

### Beyan edilmesi beklenen veri türleri

| Google Play kategorisi | Örnek SocialDiet verisi | Amaç | Zorunlu mu? |
|---|---|---|---|
| Kişisel bilgiler / E-posta | E-posta adresi | Hesap yönetimi | Evet |
| Kişisel bilgiler / Ad | Görünen ad, kullanıcı adı | Profil ve sosyal özellikler | Kullanıma bağlı |
| Kullanıcı kimlikleri | Firebase UID, kullanıcı adı | Hesap ve veri eşleştirme | Evet |
| Sağlık ve fitness | Boy, kilo, hedef kilo | Kilo ilerleme takibi | Uygulama özelliği |
| Sağlık ve fitness | Kalori hedefi ve öğün besin değerleri | Beslenme takibi | Uygulama özelliği |
| Uygulama etkinliği / Kullanıcı tarafından oluşturulan içerik | Öğün kayıtları ve hedef ilerlemesi | Uygulama özelliği | Uygulama özelliği |
| Fotoğraf ve video | Öğün fotoğrafı | Cihaz üzerinde AI analizi | **Cihazdan çıkmıyorsa Data Safety'de 'toplanan' olarak işaretlenmemeli** |

### Firebase nedeniyle tekrar kontrol edilecek teknik veriler

Firebase Authentication; kayıt/giriş sırasında IP adresi, kullanıcı aracısı ve Firebase uygulama kimliği gibi teknik bilgileri işler. Firebase SDK sürümü ve Google Play formunun güncel sınıflandırması gönderim günü Firebase'in resmi Data Safety dokümanıyla tekrar karşılaştırılmalıdır.

### Üretim öncesi kontrol

- Firebase Analytics veya Crashlytics eklenirse Data Safety beyanı yeniden değerlendirilmelidir.
- Firebase Storage ileride öğün fotoğrafları için etkinleştirilirse "Fotoğraf ve video" veri türü toplanan veri olarak beyan edilmelidir.
- Arkadaşlara görünür alanlar yalnızca gerekli profil/ilerleme verileriyle sınırlandırılmalıdır.
