package com.example.freeinternet;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class MainActivity extends AppCompatActivity {
    private EditText etHost, etPort, etUser, etPassword;
    private Button btnConnect, btnFetch, btnClear;
    private TextView tvStatus;
    private final List<String[]> latestCandidates = new ArrayList<>();
    private static final int MAX_CANDIDATES = 25;
    private static final String LATEST_SOURCE = "https://api.proxyscrape.com/v4/free-proxy-list/get?request=getproxies&protocol=http&ssl=yes&anonymity=elite&timeout=5000&limit=50";
    private static final String[] TEST_URLS = {
            "https://www.google.com/generate_204",
            "https://www.cloudflare.com/cdn-cgi/trace",
            "https://example.com/"
    };
    private static final int MAX_ATTEMPTS = 3;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etHost = findViewById(R.id.etHost); etPort = findViewById(R.id.etPort);
        etUser = findViewById(R.id.etUser); etPassword = findViewById(R.id.etPassword);
        btnConnect = findViewById(R.id.btnConnect); btnFetch = findViewById(R.id.btnFetch); btnClear = findViewById(R.id.btnClear);
        tvStatus = findViewById(R.id.tvStatus);
        loadSavedProxy();
        btnConnect.setOnClickListener(v -> startTest());
        btnFetch.setOnClickListener(v -> fetchLatestCandidates());
        btnClear.setOnClickListener(v -> { etHost.setText(""); etPort.setText(""); etUser.setText(""); etPassword.setText("");
            getPreferences(MODE_PRIVATE).edit().clear().apply(); tvStatus.setText("Proxy cleared. Ready to test direct internet."); });
    }

    private void fetchLatestCandidates() {
        btnConnect.setEnabled(false); btnFetch.setEnabled(false); btnClear.setEnabled(false);
        tvStatus.setText("Fetching recently checked HTTPS-capable HTTP proxies...\nMaximum candidates tested: " + MAX_CANDIDATES);
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build();
        Request request = new Request.Builder().url(LATEST_SOURCE).header("User-Agent", "FreeInternetConnect/2.1").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { runOnUiThread(() -> { tvStatus.setText("Could not fetch latest candidates.\n" + e.getClass().getSimpleName() + ": " + e.getMessage()); resetButtons(); }); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                int code = response.code(); String body = response.body() == null ? "" : response.body().string(); response.close();
                if (code < 200 || code >= 300) { runOnUiThread(() -> { tvStatus.setText("Candidate source returned HTTP " + code); resetButtons(); }); return; }
                latestCandidates.clear();
                for (String line : body.split("\\R")) {
                    String[] pair = line.trim().split(":");
                    if (pair.length == 2 && pair[0].matches("[0-9.]{7,15}") && pair[1].matches("[0-9]{1,5}")) {
                        int p = Integer.parseInt(pair[1]); if (p > 0 && p < 65536) latestCandidates.add(new String[]{pair[0], pair[1]});
                    }
                }
                runOnUiThread(() -> tvStatus.setText("Fetched " + latestCandidates.size() + " candidates. Testing up to " + Math.min(MAX_CANDIDATES, latestCandidates.size()) + " sequentially..."));
                testLatestCandidate(0);
            }
        });
    }

    private void testLatestCandidate(int index) {
        int total = Math.min(MAX_CANDIDATES, latestCandidates.size());
        if (index >= total) { runOnUiThread(() -> { tvStatus.setText("No recent candidate passed HTTPS verification.\nThis is normal for free public proxies; try a trusted authenticated endpoint."); resetButtons(); }); return; }
        String[] pair = latestCandidates.get(index); String host = pair[0]; int port = Integer.parseInt(pair[1]);
        runOnUiThread(() -> tvStatus.setText("Verifying candidate " + (index + 1) + "/" + total + ": " + host + ":" + port));
        OkHttpClient client = new OkHttpClient.Builder().proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)))
                .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build();
        Request request = new Request.Builder().url("https://www.google.com/generate_204").header("User-Agent", "FreeInternetConnect/2.1").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { testLatestCandidate(index + 1); }
            @Override public void onResponse(Call call, Response response) {
                int code = response.code(); response.close();
                if (code >= 200 && code < 400) runOnUiThread(() -> { etHost.setText(host); etPort.setText(String.valueOf(port)); tvStatus.setText("SUCCESS\nLatest candidate verified: " + host + ":" + port + "\nHTTPS response " + code); saveProxy(); resetButtons(); });
                else testLatestCandidate(index + 1);
            }
        });
    }

    private void startTest() {
        String host = etHost.getText().toString().trim();
        String portText = etPort.getText().toString().trim();
        final int port;
        if (host.isEmpty() && portText.isEmpty()) port = -1;
        else {
            try { port = Integer.parseInt(portText); } catch (NumberFormatException e) { tvStatus.setText("Enter a valid numeric proxy port."); return; }
            if (host.isEmpty() || port < 1 || port > 65535) { tvStatus.setText("Enter both a valid proxy host and port."); return; }
        }
        saveProxy();
        btnConnect.setEnabled(false); btnClear.setEnabled(false); btnConnect.setText("TESTING...");
        tvStatus.setText(port < 0 ? "Testing direct HTTPS connectivity..." : "Testing trusted proxy with HTTPS...\nRetries: 0/" + MAX_ATTEMPTS);
        testAttempt(host, port, 0, 0, null);
    }

    private void testAttempt(String host, int port, int attempt, int urlIndex, String lastError) {
        if (attempt >= MAX_ATTEMPTS) {
            runOnUiThread(() -> { tvStatus.setText("FAILED after " + MAX_ATTEMPTS + " attempts.\n" + (lastError == null ? "No HTTPS response." : lastError) + "\n\nCheck host, port, credentials, firewall, and whether the endpoint supports HTTP CONNECT."); resetButtons(); });
            return;
        }
        if (urlIndex >= TEST_URLS.length) {
            try { Thread.sleep(600L * (attempt + 1)); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            testAttempt(host, port, attempt + 1, 0, lastError);
            return;
        }
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(true);
        if (port > 0) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
            String user = etUser.getText().toString(); String password = etPassword.getText().toString();
            if (!user.isEmpty()) builder.proxyAuthenticator((route, response) -> {
                if (response.request().header("Proxy-Authorization") != null) return null;
                return response.request().newBuilder().header("Proxy-Authorization", Credentials.basic(user, password)).build();
            });
        }
        Request request = new Request.Builder().url(TEST_URLS[urlIndex]).header("User-Agent", "FreeInternetConnect/2.0").build();
        final int nextUrl = urlIndex + 1;
        builder.build().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> tvStatus.setText("Testing... attempt " + (attempt + 1) + "/" + MAX_ATTEMPTS + "\n" + e.getClass().getSimpleName() + ": " + e.getMessage()));
                testAttempt(host, port, attempt, nextUrl, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) {
                int code = response.code(); response.close();
                if (code >= 200 && code < 400) runOnUiThread(() -> { tvStatus.setText("SUCCESS\nHTTPS verified with response " + code + (port > 0 ? " via " + host + ":" + port : " directly")); resetButtons(); });
                else testAttempt(host, port, attempt, nextUrl, "HTTP response " + code);
            }
        });
    }

    private void resetButtons() { btnConnect.setEnabled(true); btnFetch.setEnabled(true); btnClear.setEnabled(true); btnConnect.setText("TEST AGAIN"); }
    private void saveProxy() { getPreferences(MODE_PRIVATE).edit().putString("host", etHost.getText().toString()).putString("port", etPort.getText().toString()).putString("user", etUser.getText().toString()).apply(); }
    private void loadSavedProxy() { android.content.SharedPreferences p = getPreferences(MODE_PRIVATE); etHost.setText(p.getString("host", "")); etPort.setText(p.getString("port", "")); etUser.setText(p.getString("user", "")); }
}
