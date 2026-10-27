package com.rk.publicbridge;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

final class UpnpPublisher {
    private static final int INTERNAL_PORT = 8080;
    private static final int EXTERNAL_PORT = 18080;
    private static final String CHARSET = "UTF-8";

    static final class Result {
        final boolean success;
        final String message;
        Result(boolean ok, String msg) { success = ok; message = msg; }
    }

    static String getWifiIp(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "Wi-Fi tidak tersedia";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getIpAddress() == 0) return "Tidak terhubung ke Wi-Fi";
            int ip = info.getIpAddress();
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) { return "Tidak bisa membaca IP Wi-Fi"; }
    }

    static Result publish(Context context) {
        String localIp = getWifiIp(context);
        if (localIp == null || !localIp.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || localIp.startsWith("Tidak")) {
            return new Result(false, "Tablet belum terhubung ke Wi-Fi. Hubungkan dulu ke Wi-Fi rumah.");
        }
        String localCheck = httpGet("http://" + localIp + ":" + INTERNAL_PORT + "/", 2500);
        if (localCheck == null) {
            return new Result(false, "Tidak bisa menghubungi http://" + localIp + ":8080/.\n\nBuka aplikasi RK Server utama dan pastikan statusnya SERVER AKTIF sebelum menekan tombol ini.");
        }
        try {
            Gateway gateway = findGateway(context);
            if (gateway == null) return new Result(false, "Router tidak merespons pencarian UPnP/IGD. Pastikan tablet dan router Wi-Fi tersambung, lalu cek apakah UPnP di router tersedia/diaktifkan.\n\nTidak ada port yang berhasil dibuka.");
            String add = soap(gateway, "AddPortMapping", "<NewRemoteHost></NewRemoteHost>" +
                    "<NewExternalPort>" + EXTERNAL_PORT + "</NewExternalPort>" +
                    "<NewProtocol>TCP</NewProtocol>" +
                    "<NewInternalPort>" + INTERNAL_PORT + "</NewInternalPort>" +
                    "<NewInternalClient>" + localIp + "</NewInternalClient>" +
                    "<NewEnabled>1</NewEnabled>" +
                    "<NewPortMappingDescription>RK Server test</NewPortMappingDescription>" +
                    "<NewLeaseDuration>3600</NewLeaseDuration>");
            if (add == null || add.contains("<UPnPError") || add.contains("<errorCode>")) {
                return new Result(false, "Router ditemukan, tetapi menolak pemetaan port TCP " + EXTERNAL_PORT + ".\n\n" + briefError(add) + "\n\nTidak ada jalur publik yang dikonfirmasi.");
            }
            String wanIp = soap(gateway, "GetExternalIPAddress", "");
            String routerPublicIp = extractTag(wanIp, "NewExternalIPAddress");
            String seenIp = httpGet("http://api.ipify.org", 6000);
            if (seenIp != null) seenIp = seenIp.trim();
            boolean publicAddressMatches = seenIp != null && routerPublicIp != null && seenIp.equals(routerPublicIp.trim()) && isPublicIpv4(seenIp);
            StringBuilder sb = new StringBuilder();
            sb.append("Router menerima pemetaan TCP ").append(EXTERNAL_PORT).append(" → ").append(localIp).append(":").append(INTERNAL_PORT).append(".\n\n");
            sb.append("IP lokal tablet: ").append(localIp).append("\nIP WAN router: ").append(routerPublicIp == null ? "tidak terbaca" : routerPublicIp).append("\nIP yang terlihat dari internet: ").append(seenIp == null ? "tidak berhasil diperiksa" : seenIp).append("\n\n");
            if (publicAddressMatches) {
                sb.append("URL percobaan: http://").append(seenIp).append(":").append(EXTERNAL_PORT).append("/\n\n");
                sb.append("Penting: ini baru hasil pemetaan UPnP dan pemeriksaan IP; koneksi dari jaringan luar tetap perlu diuji dari HP dengan data seluler. Router bisa memblokir akses dari dalam jaringan sendiri.\n\n");
                sb.append("Pemetaan ini berlaku sekitar 1 jam lalu kedaluwarsa otomatis. Tekan MATIKAN / HAPUS PORT PUBLIK lebih awal setelah tes. Jika ingin menjalankan terus-menerus, kita perlu menambah pembaruan lease dan pengamanan server.");
                return new Result(true, sb.toString());
            }
            sb.append("Router mungkin berada di balik CGNAT/double NAT, atau alamat WAN tidak sama dengan IP internet. Karena itu URL publik belum bisa dijamin berfungsi. Hubungi ISP untuk menanyakan IP publik, atau gunakan tunnel/relay.\n\n");
            try { soap(gateway, "DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + EXTERNAL_PORT + "</NewExternalPort><NewProtocol>TCP</NewProtocol>"); } catch (Exception ignored) {}
            sb.append("Pemetaan sementara telah diminta untuk dihapus karena alamat internet publik tidak cocok. Jika router tetap menyimpan aturan, hapus TCP port 18080 di pengaturan router.");
            return new Result(false, sb.toString());
        } catch (Exception e) {
            return new Result(false, "Gagal membuat pemetaan port: " + safeMessage(e) + "\n\nPastikan Wi-Fi aktif dan router mendukung UPnP. Jika tidak, APK ini tidak bisa membuat server publik sendiri.");
        }
    }

    static Result remove(Context context) {
        try {
            Gateway gateway = findGateway(context);
            if (gateway == null) return new Result(false, "Router tidak ditemukan lewat UPnP; aplikasi tidak bisa mengirim perintah penghapusan. Periksa pengaturan port forwarding router secara manual.");
            String response = soap(gateway, "DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + EXTERNAL_PORT + "</NewExternalPort><NewProtocol>TCP</NewProtocol>");
            if (response == null || response.contains("<UPnPError") || response.contains("<errorCode>")) {
                return new Result(false, "Router tidak mengonfirmasi penghapusan. Jika port pernah dibuat manual, hapus mapping TCP port 18080 di pengaturan router.");
            }
            return new Result(true, "Perintah penghapusan port TCP 18080 dikirim ke router. Jika pemetaan itu dibuat secara manual atau dengan router lain, periksa pengaturan router juga.");
        } catch (Exception e) {
            return new Result(false, "Gagal menghapus port: " + safeMessage(e) + ". Periksa pengaturan router bila perlu.");
        }
    }

    private static Gateway findGateway(Context context) throws Exception {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = null;
        if (wm != null) {
            try { lock = wm.createMulticastLock("rk-public-ssdp"); lock.setReferenceCounted(false); lock.acquire(); } catch (Exception ignored) {}
        }
        Set<String> locations = new HashSet<String>();
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(400);
            socket.setBroadcast(true);
            String request = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
            byte[] bytes = request.getBytes(Charset.forName("US-ASCII"));
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName("239.255.255.250"), 1900);
            socket.send(packet);
            long end = System.currentTimeMillis() + 2600;
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < end) {
                try {
                    DatagramPacket response = new DatagramPacket(buf, buf.length);
                    socket.receive(response);
                    String text = new String(response.getData(), response.getOffset(), response.getLength(), Charset.forName("US-ASCII"));
                    String loc = header(text, "LOCATION");
                    if (loc != null && loc.startsWith("http")) locations.add(loc.trim());
                } catch (java.net.SocketTimeoutException timeout) { /* keep waiting until deadline */ }
            }
        } finally {
            if (socket != null) socket.close();
            if (lock != null) try { lock.release(); } catch (Exception ignored) {}
        }
        for (String location : locations) {
            try {
                String xml = httpGet(location, 3500);
                if (xml == null) continue;
                Document doc = parseXml(xml);
                String baseText = firstTag(doc, "URLBase");
                URL base = (baseText != null && baseText.trim().length() > 0) ? new URL(baseText.trim()) : new URL(location);
                NodeList services = doc.getElementsByTagName("service");
                for (int i = 0; i < services.getLength(); i++) {
                    Element service = (Element) services.item(i);
                    String type = childText(service, "serviceType");
                    String control = childText(service, "controlURL");
                    if (type != null && control != null && (type.contains("WANIPConnection") || type.contains("WANPPPConnection"))) {
                        URL controlUrl = new URL(base, control.trim());
                        return new Gateway(type.trim(), controlUrl.toString());
                    }
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String soap(Gateway gateway, String action, String args) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
                "<u:" + action + " xmlns:u=\"" + gateway.serviceType + "\">" + args + "</u:" + action + ">" +
                "</s:Body></s:Envelope>";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(gateway.controlUrl).openConnection();
            conn.setConnectTimeout(4000); conn.setReadTimeout(5000); conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPAction", "\"" + gateway.serviceType + "#" + action + "\"");
            byte[] payload = body.getBytes(Charset.forName(CHARSET));
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream(); out.write(payload); out.close();
            InputStream in;
            try { in = conn.getInputStream(); } catch (Exception e) { in = conn.getErrorStream(); }
            if (in == null) return null;
            return readAll(in);
        } finally { if (conn != null) conn.disconnect(); }
    }

    private static String httpGet(String address, int timeout) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(timeout); conn.setReadTimeout(timeout); conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RK-Public-Bridge/1.0");
            InputStream in = conn.getInputStream();
            String result = readAll(in);
            return result == null ? null : result.trim();
        } catch (Exception e) { return null; }
        finally { if (conn != null) conn.disconnect(); }
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        return f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml.getBytes(Charset.forName(CHARSET)))));
    }

    private static String firstTag(Document doc, String tag) { NodeList n = doc.getElementsByTagName(tag); return n.getLength() > 0 ? n.item(0).getTextContent() : null; }
    private static String childText(Element parent, String tag) { NodeList n = parent.getElementsByTagName(tag); return n.getLength() > 0 ? n.item(0).getTextContent() : null; }
    private static String extractTag(String xml, String tag) {
        if (xml == null) return null;
        try { return firstTag(parseXml(xml), tag); } catch (Exception e) { return null; }
    }
    private static String header(String response, String name) {
        String[] lines = response.split("\\r?\\n");
        for (String line : lines) { int p = line.indexOf(':'); if (p > 0 && line.substring(0, p).trim().equalsIgnoreCase(name)) return line.substring(p + 1).trim(); }
        return null;
    }
    private static String readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[2048]; int n;
            while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            return new String(out.toByteArray(), Charset.forName(CHARSET));
        } finally { try { in.close(); } catch (Exception ignored) {} }
    }
    private static String briefError(String xml) {
        if (xml == null) return "Router tidak memberikan detail error.";
        String code = extractTag(xml, "errorCode"); String desc = extractTag(xml, "errorDescription");
        return "Kode router: " + (code == null ? "tidak diketahui" : code) + (desc == null ? "" : " — " + desc);
    }
    private static boolean isPublicIpv4(String ip) {
        if (ip == null || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return false;
        String[] a = ip.split("\\."); int x = Integer.parseInt(a[0]), y = Integer.parseInt(a[1]);
        if (x == 10 || x == 127 || x == 0 || (x == 172 && y >= 16 && y <= 31) || (x == 192 && y == 168) || (x == 169 && y == 254) || (x == 100 && y >= 64 && y <= 127) || x >= 224) return false;
        return true;
    }
    private static String safeMessage(Exception e) { String s = e.getMessage(); return s == null ? e.getClass().getSimpleName() : s; }
    private static final class Gateway {
        final String serviceType; final String controlUrl;
        Gateway(String t, String u) { serviceType = t; controlUrl = u; }
    }
}
