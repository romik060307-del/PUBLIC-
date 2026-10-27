# RK Public Bridge (Android 4.4+)

Proyek APK pendamping untuk mencoba menerbitkan RK Server lokal yang sudah berjalan pada tablet. Aplikasi ini **tidak menggantikan RK Server** dan tidak membuat layanan relay baru. Ia mencoba melakukan port-forwarding UPnP/IGD pada router Wi-Fi rumah:

- Periksa apakah `http://IP_TABLET:8080/` merespons.
- Cari router UPnP/IGD di jaringan Wi-Fi.
- Minta router memetakan TCP port eksternal `18080` ke tablet port `8080`.
- Bandingkan IP WAN router dengan IP yang terlihat dari internet.
- Jika cocok dan terlihat publik, tampilkan URL `http://IP_PUBLIK:18080/`.
- Sediakan tombol untuk menghapus pemetaan TCP port 18080.
- Pemetaan percobaan meminta lease 3600 detik (sekitar satu jam), sehingga router dapat menghapusnya otomatis setelah masa itu.

## Batas teknis

Aplikasi hanya berhasil jika router mendukung UPnP/IGD, Wi-Fi lokal mengizinkan SSDP multicast, dan koneksi internet memiliki IP WAN publik yang dapat menerima koneksi masuk. CGNAT, double NAT, firewall/ISP yang memblokir koneksi masuk, atau router tanpa UPnP akan menggagalkan akses publik. Jika gagal, opsi berikutnya adalah meminta IP publik kepada ISP atau memakai relay/tunnel yang memiliki titik akhir publik; APK saja tidak bisa menciptakan IP publik dari ketiadaan.

## PERINGATAN KEAMANAN

RK Server versi saat ini adalah prototipe HTTP tanpa login dan tanpa enkripsi. Ketika port dibuka, siapa pun yang dapat menjangkau alamat IP/port dapat mencoba mengakses halaman/API yang tersedia. Karena itu, gunakan hanya untuk pengujian singkat dan jangan masukkan pesan pribadi. Tekan **MATIKAN / HAPUS PORT PUBLIK** sesudah pengujian. Belum aman untuk produksi maupun layanan chat orang banyak.

URL HTTP juga tidak mengenkripsi lalu lintas. Port forwarding bukan pengganti HTTPS, autentikasi, kontrol akses, penyimpanan aman, atau enkripsi pesan.

## Membuat APK lewat GitHub Actions

1. Buat repository GitHub baru, misalnya `rk-public-bridge` (jangan menimpa repository RK Server).
2. Unggah **seluruh isi folder proyek** ini ke akar repository. Pastikan file `.github/workflows/build-apk.yml` ada di jalur itu dan `app/build.gradle` ada di root folder `app`.
3. Buka tab **Actions**, pilih **Build RK Public Bridge APK**, kemudian jalankan **Run workflow** jika belum berjalan otomatis.
4. Setelah build hijau, unduh artifact **RK-Public-Bridge-APK.zip** dari halaman proses, ekstrak, lalu ambil `app-debug.apk`.
5. Pasang APK ini di tablet yang sama, berdampingan dengan APK **RK Server** yang sudah ada.

## Cara uji

1. Sambungkan tablet ke Wi-Fi rumah.
2. Buka aplikasi **RK Server** yang lama dahulu, lalu tekan **MULAI SERVER** dan pastikan `SERVER AKTIF`.
3. Buka **RK Public Bridge**, tekan **AKTIFKAN AKSES PUBLIK**, baca peringatannya lalu konfirmasi untuk tes singkat.
4. Jika hasilnya menampilkan URL publik, uji dari HP lain dengan **mematikan Wi-Fi dan memakai data seluler**. Jangan hanya uji dari Wi-Fi rumah karena router mungkin tidak mendukung NAT loopback.
5. Tekan **MATIKAN / HAPUS PORT PUBLIK** sesudah tes. Lease juga diminta kedaluwarsa otomatis setelah sekitar satu jam.

APK ini tidak dapat diuji pada tablet secara langsung dari lingkungan build, jadi kompatibilitas router dan akses internet publik harus dibuktikan pada perangkat pengguna. Jika muncul kegagalan, kirim teks hasil pada aplikasi; jangan langsung membuka port secara manual.
