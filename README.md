# Quran Follow Reader

Prototype aplikasi Android untuk mengikuti bacaan Al-Qur'an secara visual.

## Fitur yang sudah dibuat
- Mendengar bacaan melalui mikrofon perangkat.
- Menyorot kata aktif yang sedang terbaca.
- Menyorot baris aktif tempat kata itu berada.
- Auto-scroll: ketika bacaan bergerak ke bagian bawah tampilan, layar otomatis menggeser agar bacaan naik ke atas dan tetap nyaman diikuti.
- Workflow GitHub Actions untuk membangun file APK debug.

## Status implementasi saat ini
Versi di repo ini adalah **MVP Android native dengan Jetpack Compose**.

Yang sudah ada:
- Tampilan mushaf demo berbasis Surah Al-Fatihah.
- Pengenalan suara Android `SpeechRecognizer` dengan bahasa Arab (`ar-SA`).
- Logika normalisasi huruf Arab sederhana.
- Pelacakan kata dari awal bacaan.

## Catatan penting
Akurasi pengenalan kata Al-Qur'an dengan `SpeechRecognizer` bawaan Android belum setara sistem tajwid atau forced-alignment khusus Qur'an. Jadi repo ini adalah fondasi kerja yang sudah berjalan untuk:
1. mendengar bacaan,
2. menggerakkan highlight kata/baris,
3. melakukan auto-scroll.

Untuk versi produksi yang lebih presisi, tahap berikutnya biasanya perlu:
- model ASR Arab yang lebih kuat,
- forced alignment per kata,
- dataset mushaf per halaman dan koordinat kata,
- mode lanjutan untuk melanjutkan dari tengah ayat, bukan hanya dari awal.

## Build APK di GitHub
Repo ini sudah memiliki workflow:
- `.github/workflows/android-apk.yml`

Saat push ke branch `main` atau menjalankan workflow manual, GitHub Actions akan mencoba membangun:
- `app/build/outputs/apk/debug/app-debug.apk`

## Struktur utama
- `app/src/main/java/com/imamalt/quranfollow/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `.github/workflows/android-apk.yml`

## Langkah lanjut yang paling masuk akal
- Ganti data demo Al-Fatihah menjadi data mushaf halaman penuh.
- Tambahkan koordinat kata nyata per halaman.
- Upgrade engine pengenalan dari speech recognition umum menjadi engine khusus Qur'an.
- Tambahkan mode scroll berdasarkan posisi visual bawah layar, bukan hanya index baris aktif.
