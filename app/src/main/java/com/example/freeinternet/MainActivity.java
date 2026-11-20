package com.example.freeinternet;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private Button btnConnect;
    private TextView tvStatus;

    // Public proxies are inherently untrusted and can disappear without notice.
    private final String[][] PROXY_LIST = {
        {"103.152.112.162", "80"},
        {"185.169.215.200", "8080"},
        {"45.77.177.229", "3128"},
        {"103.216.227.168", "80"}
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnConnect = findViewById(R.id.btnConnect);
        tvStatus = findViewById(R.id.tvStatus);
        btnConnect.setOnClickListener(v -> {
            btnConnect.setEnabled(false);
            btnConnect.setText("Testing proxies...");
            tvStatus.setText("Scanning " + PROXY_LIST.length + " public nodes...");
            tryConnection(0);
        });
    }

    private void tryConnection(int index) {
        if (index >= PROXY_LIST.length) {
            runOnUiThread(() -> {
                tvStatus.setText("No tested proxy responded.");
                btnConnect.setEnabled(true);
                btnConnect.setText("Retry");
            });
            return;
        }
        String host = PROXY_LIST[index][0];
        int port = Integer.parseInt(PROXY_LIST[index][1]);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy).connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build();
        Request request = new Request.Builder().url("https://www.google.com")
                .header("User-Agent", "FreeInternetConnect/1.0").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { tryConnection(index + 1); }
            @Override public void onResponse(Call call, Response response) {
                response.close();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Proxy responded: " + host + ":" + port);
                        btnConnect.setText("Test again");
                        btnConnect.setEnabled(true);
                    });
                } else { tryConnection(index + 1); }
            }
        });
    }
}
