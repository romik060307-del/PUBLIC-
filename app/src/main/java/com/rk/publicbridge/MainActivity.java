package com.rk.publicbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView detailsText;
    private Button publishButton;
    private Button removeButton;
    private final int dark = Color.rgb(18, 63, 55);
    private final int green = Color.rgb(23, 107, 91);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        detailsText.setText("IP tablet: " + UpnpPublisher.getWifiIp(this) + "\nPort server lokal: 8080\nPort publik percobaan: 18080\n\nPastikan aplikasi RK Server yang lama sedang aktif sebelum menerbitkan server.");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(247, 249, 248));
        scroll.addView(root);

        TextView brand = new TextView(this);
        brand.setText("RK PUBLIC BRIDGE");
        brand.setTextColor(dark);
        brand.setTextSize(25);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(null, 1);
        root.addView(brand, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Penghubung akses internet ke RK Server di tablet");
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = matchWrap(); subLp.topMargin = dp(8); subLp.bottomMargin = dp(20);
        root.addView(subtitle, subLp);

        statusText = new TextView(this);
        statusText.setText("STATUS: BELUM DIPUBLIKASIKAN");
        statusText.setTextSize(16);
        statusText.setTypeface(null, 1);
        statusText.setTextColor(dark);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(12), dp(16), dp(12), dp(16));
        statusText.setBackgroundColor(Color.rgb(226, 238, 234));
        root.addView(statusText, matchWrap());

        detailsText = new TextView(this);
        detailsText.setTextColor(Color.rgb(49, 62, 58));
        detailsText.setTextSize(14);
        detailsText.setPadding(dp(2), dp(16), dp(2), dp(16));
        root.addView(detailsText, matchWrap());

        publishButton = makeButton("AKTIFKAN AKSES PUBLIK", green);
        root.addView(publishButton, buttonLp());
        publishButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRiskWarning(); }
        });

        removeButton = makeButton("MATIKAN / HAPUS PORT PUBLIK", Color.rgb(95, 76, 72));
        LinearLayout.LayoutParams removeLp = buttonLp(); removeLp.topMargin = dp(10);
        root.addView(removeButton, removeLp);
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runPublishTask(true); }
        });

        TextView note = new TextView(this);
        note.setText("PENTING\nServer lokal yang sekarang adalah prototipe dan belum memiliki login, enkripsi, atau pembatasan akses yang memadai. Jika port dibuka, orang dari internet bisa mencoba mengaksesnya. Gunakan hanya untuk tes singkat dan tekan MATIKAN setelah selesai. Aplikasi ini hanya mencoba pemetaan port UPnP pada router; tidak dapat melewati CGNAT, double NAT, atau router yang menolak UPnP.");
        note.setTextColor(Color.rgb(142, 54, 43));
        note.setTextSize(13);
        note.setPadding(dp(12), dp(14), dp(12), dp(14));
        note.setBackgroundColor(Color.rgb(255, 239, 234));
        LinearLayout.LayoutParams noteLp = matchWrap(); noteLp.topMargin = dp(18);
        root.addView(note, noteLp);
        setContentView(scroll);
    }

    private void showRiskWarning() {
        new AlertDialog.Builder(this)
            .setTitle("Terbitkan server ke internet?")
            .setMessage("Server RK yang ada sekarang belum aman untuk internet publik. Pengunjung internet bisa mencoba membuka halaman dan API chat. Lanjutkan hanya untuk tes singkat, dan hapus port publik setelah tes.\n\nAplikasi akan mencoba UPnP pada router rumah. Jika koneksi memakai CGNAT atau router tidak mendukung UPnP, akses publik tetap tidak bisa dibuat.")
            .setNegativeButton("BATAL", null)
            .setPositiveButton("SAYA PAHAM, COBA", (dialog, which) -> runPublishTask(false))
            .show();
    }

    private void runPublishTask(final boolean remove) {
        publishButton.setEnabled(false);
        removeButton.setEnabled(false);
        statusText.setText(remove ? "SEDANG MENGHAPUS PORT..." : "SEDANG MEMERIKSA ROUTER...");
        detailsText.setText("Tunggu sebentar. Pastikan Wi-Fi aktif dan RK Server berjalan pada port 8080.");
        new Thread(new Runnable() {
            @Override public void run() {
                final UpnpPublisher.Result r = remove ? UpnpPublisher.remove(MainActivity.this) : UpnpPublisher.publish(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        publishButton.setEnabled(true);
                        removeButton.setEnabled(true);
                        statusText.setText(r.success ? (remove ? "PORT PUBLIK DIHAPUS" : "PEMETAAN PORT BERHASIL") : (remove ? "PENGHAPUSAN GAGAL" : "BELUM BISA DIAKSES PUBLIK"));
                        detailsText.setText(r.message);
                    }
                });
            }
        }).start();
    }

    private Button makeButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false);
        b.setTypeface(null, 1); b.setBackgroundColor(color);
        return b;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams buttonLp() { LinearLayout.LayoutParams p = matchWrap(); p.height = dp(54); return p; }
    private int dp(int x) { return (int)(getResources().getDisplayMetrics().density * x + 0.5f); }
}
