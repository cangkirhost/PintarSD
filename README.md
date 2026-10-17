# Pintar SD – Kunci Tablet dengan Soal

Aplikasi Android yang menjadi **layar utama (launcher)** tablet anak. Saat tablet dibuka, anak harus menyelesaikan misi soal (Matematika / Bahasa Inggris sesuai kelas) dulu. Anak bisa memilih sendiri jalur **Kurikulum Merdeka** atau **Cambridge Primary (Stage 1–6)** di layar awal; tampilan tetap berbahasa Indonesia. Setelah misi selesai, tablet terbuka selama waktu yang diatur orang tua, lalu terkunci lagi otomatis.

## Cara kerja

| Keadaan | Yang tampil |
|---|---|
| Terkunci | Halaman soal Pintar SD. Tombol Kembali tidak berfungsi; kalau anak pindah lewat tombol Recent, layar soal muncul lagi. |
| Misi selesai | Tombol **Buka tablet**. Tablet terbuka selama X menit. |
| Terbuka | Daftar aplikasi + sisa waktu bebas. |
| Waktu habis | Layar soal muncul lagi otomatis, walaupun anak sedang di aplikasi lain. |

Menu orang tua (dengan PIN) untuk mengatur:

- kelas anak (1–6)
- jumlah latihan yang harus diselesaikan
- minimal jawaban benar per latihan (dari 10)
- lama waktu bebas (menit)
- buka / kunci tablet seketika
- riwayat latihan anak (judul, tanggal, skor)

## 1. Membuat file APK

Kode ini belum dikompilasi. Pilih salah satu cara:

### Cara A – GitHub (tanpa install apa pun)

1. Buat repository baru (boleh private) di github.com.
2. Upload **seluruh isi folder ini**, termasuk folder `.github`.
   Jika folder `.github` tidak ikut terupload, buat manual lewat *Add file → Create new file*, beri nama `.github/workflows/build.yml`, lalu salin isinya dari file di folder ini.
3. Buka tab **Actions** → workflow **Build APK** berjalan otomatis (sekitar 3–5 menit). Bisa juga dijalankan manual lewat *Run workflow*.
4. Setelah centang hijau, buka run tersebut → bagian **Artifacts** → unduh **PintarSD-apk** (zip berisi `app-debug.apk`).

### Cara B – Android Studio

1. Install Android Studio, pilih *Open* → folder `PintarSD`.
2. Tunggu proses sync selesai (pertama kali butuh internet).
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Memasang di tablet

1. Kirim `app-debug.apk` ke tablet (kabel USB, Google Drive, dsb.) lalu buka filenya.
2. Izinkan "Install aplikasi tidak dikenal" jika diminta.

> **Catatan Family Link:** akun anak yang diawasi biasanya **tidak bisa memasang APK dari luar Play Store**. Jika diblokir, pilihannya:
> - Pasang lewat komputer dengan ADB: aktifkan *Opsi pengembang → USB debugging* (di Family Link mungkin perlu diizinkan orang tua), lalu jalankan `adb install app-debug.apk`.
> - Atau unggah ke Google Play Console sebagai *internal testing* (akun developer Google, biaya sekali bayar), lalu setujui pemasangannya dari Family Link seperti aplikasi biasa.

## 3. Pengaturan pertama (wajib)

1. Buka **Pintar SD** → buat PIN orang tua.
2. Di menu orang tua, tekan tombol berurutan:
   1. **Jadikan Pintar SD layar utama** → pilih Pintar SD → **Selalu**.
   2. **Izinkan tampil di atas aplikasi lain** → aktifkan. *(Wajib, supaya layar soal bisa muncul saat waktu habis.)*
   3. **Izinkan alarm tepat waktu** (Android 12+) → aktifkan.
3. Atur kelas, jumlah latihan, nilai minimum, dan waktu bebas → **Simpan pengaturan**.
4. Tekan **Kunci tablet sekarang** untuk mencoba.

Semua status izin terlihat di bagian **Status** pada menu orang tua (✓ / ✗).

## 4. Saran memakai Family Link bersama Pintar SD

- Tetap gunakan Family Link untuk menyetujui aplikasi baru dan membatasi waktu layar harian.
- Jika perangkat mengizinkan, blokir aplikasi **Setelan/Settings** di Family Link agar anak tidak bisa mengganti layar utama. Pada banyak perangkat, Setelan termasuk aplikasi sistem yang tidak bisa diblokir.

## Batasan versi ini (prototipe)

- Ini **bukan** pengganti layar kunci sistem (PIN/pola Android tetap berjalan seperti biasa). Pintar SD muncul tepat setelah layar kunci sistem dibuka.
- Anak yang paham pengaturan Android bisa mengganti layar utama lewat Setelan bila Setelan tidak diblokir.
- Panel notifikasi / quick settings tetap bisa ditarik.
- Suara Bahasa Inggris memakai mesin Text-to-Speech tablet (Google TTS). Jika tidak bersuara, pasang/aktifkan "Speech Services by Google".
- Font khusus dimuat dari internet; tanpa internet tampilan memakai font bawaan dan tetap berfungsi.
- Untuk penguncian penuh (kiosk), aplikasi perlu dijadikan *Device Owner*; ini bisa dikembangkan di versi berikutnya.

## Struktur proyek

```
app/src/main/
├── AndroidManifest.xml
├── assets/www/index.html        ← soal Pintar SD (versi web + jembatan ke Android)
├── java/net/cangkirhost/pintarsd/
│   ├── MainActivity.java        ← launcher: layar soal / daftar aplikasi
│   ├── ParentActivity.java      ← menu orang tua (PIN, pengaturan, riwayat)
│   ├── Store.java               ← penyimpanan pengaturan, status kunci, alarm
│   ├── ExpireReceiver.java      ← mengunci lagi saat waktu bebas habis
│   └── BootReceiver.java        ← memasang ulang alarm setelah restart
└── res/drawable/ic_launcher.xml
```
