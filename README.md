# Quran Follow Reader

Prototype aplikasi Android untuk mengikuti bacaan Al-Qur'an secara visual dengan penguncian posisi bacaan yang lebih presisi.

## Yang sekarang sudah ada di repo
- Dukungan **114 surat penuh** melalui daftar surat lengkap.
- Pemuatan isi surat **secara dinamis** dari dataset Qur'an JSON.
- Mendengar bacaan lewat mikrofon perangkat.
- Menyorot **kata aktif** yang sedang terkunci.
- Menyorot **ayat aktif** tempat kata itu berada.
- **Auto-scroll**: ketika posisi bacaan bergerak ke bawah, layar otomatis menggeser agar ayat aktif naik ke atas.
- Workflow GitHub Actions untuk build APK debug.

## Perubahan penting dari versi sebelumnya
Versi awal hanya demo Al-Fatihah dengan pelacakan sederhana. Sekarang struktur aplikasinya sudah diubah menjadi:
- navigator **114 surat**,
- loader surat berbasis data JSON,
- matcher konteks yang tidak langsung percaya pada satu kata tunggal.

## Logika presisi baru
Karena dalam satu halaman atau satu surat bisa muncul kata yang sama berkali-kali, pencocokan sekarang memakai beberapa lapis:
1. kata yang sedang terbaca,
2. urutan frasa/token terakhir,
3. kandidat posisi terbaik dalam surat aktif,
4. preferensi pada konteks yang **melintasi minimal 2 ayat**.

Jika konteks belum cukup kuat, sistem **menahan posisi** dan tidak langsung melompat ke kata yang sama di tempat lain.

## Status implementasi saat ini
Versi ini adalah **MVP Android native dengan Jetpack Compose** yang sudah jauh lebih dekat ke kebutuhan nyata pembaca Qur'an.

Yang sudah diwujudkan:
- daftar 114 surat,
- pemilihan surat,
- load isi surat,
- highlight kata,
- highlight ayat,
- auto-scroll,
- matcher berbasis konteks.

## Catatan jujur
Akurasi masih dibatasi oleh `SpeechRecognizer` Android umum. Jadi, walaupun matcher sudah dibuat lebih hati-hati, sistem ini **belum** setara forced-alignment khusus Qur'an atau engine tajwid khusus.

Artinya:
- struktur aplikasinya sudah siap untuk Qur'an utuh,
- logika ambiguitas kata berulang sudah ditingkatkan,
- tetapi presisi final tetap akan jauh lebih baik bila nanti diganti ke engine ASR/aligner khusus Qur'an.

## Sumber data
Aplikasi ini saat ini dirancang memuat daftar surat dan isi surat dari ekosistem `quran-json` / CDN yang menyediakan 114 surat dalam format JSON. Sumber teks Utsmani pada dataset tersebut dijelaskan oleh proyek sumbernya.

## Build APK di GitHub
Repo ini memiliki workflow:
- `.github/workflows/android-apk.yml`

Saat push ke `main` atau menjalankan workflow manual, GitHub Actions akan mencoba membangun:
- `app/build/outputs/apk/debug/app-debug.apk`

## Struktur utama
- `app/src/main/java/com/imamalt/quranfollow/MainActivity.kt`
- `app/src/main/java/com/imamalt/quranfollow/QuranEngine.kt`
- `app/src/main/java/com/imamalt/quranfollow/QuranMatcher.kt`
- `app/src/main/AndroidManifest.xml`
- `.github/workflows/android-apk.yml`

## Langkah berikutnya yang paling penting
- Tambahkan **cache lokal/offline** agar 114 surat tidak bergantung pada internet setelah pertama kali dimuat.
- Ganti mode ayat-list menjadi **layout mushaf per halaman** dengan koordinat kata nyata.
- Tambahkan forced alignment khusus Qur'an agar presisi kata jauh lebih tinggi.
- Tambahkan mode melanjutkan tracking dari tengah surat / tengah ayat.

<!-- workflow trigger refresh 3 -->
