package com.example.tco2display.legacy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements ProviderInstaller.ProviderInstallListener {

    private SevenSegmentView segView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private IntanglesRepository repo;
    private static final long REFRESH_MS = 2000L; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep the screen on for kiosk-style display
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        segView = findViewById(R.id.segView);

        // Try to upgrade the device security provider (TLS 1.2 for old devices)
        try {
            ProviderInstaller.installIfNeededAsync(this, this);
        } catch (Exception ignored) { /* best-effort */ }

        // ---- OkHttp 3.12.x + Retrofit 2.9 with TLS 1.2 forced on pre-Lollipop ----
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient.Builder okBuilder = new OkHttpClient.Builder()
                .addInterceptor(log)
                .retryOnConnectionFailure(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // Force TLS 1.2 on API 16–21 devices (Android 4.x)
        if (android.os.Build.VERSION.SDK_INT >= 16 && android.os.Build.VERSION.SDK_INT < 22) {
            try {
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                okBuilder.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();
                okBuilder.connectionSpecs(Arrays.asList(
                        cs, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT
                ));
            } catch (Exception ignored) { /* continue without hard TLS if it fails */ }
        }

        OkHttpClient client = okBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://apis.intangles.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        repo = new IntanglesRepository(retrofit.create(IntanglesApi.class));

        // Start periodic fetch
        tick();
    }

    private void tick() {
        io.submit(new Runnable() {
            @Override public void run() {
                try {
                    double tco2 = repo.fetchAndSumTco2(
                            BuildConfig.INTANGLES_TOKEN,                           // token (can be "")
                            "962759605811675136",                                  // accId
                            "966986020958502912,969208267156750336",               // specIds
                            300,                                                   // psize
                            "en",                                                  // lang
                            true,                                                  // noDefaultFields
                            "total_fuel_consumed",                                 // proj
                            "",                                                    // groups
                            true,                                                  // lastloc
                            "kg",                                                  // LNG unit
                            0.45                                                   // LNG density (kg/L)
                    );
                    ui.post(new Runnable() { @Override public void run() { segView.setTco2(tco2); }});
                } catch (Exception e) {
                    // Keep last value on any failure; optionally log if you need
                } finally {
                    ui.postDelayed(new Runnable() { @Override public void run() { tick(); }}, REFRESH_MS);
                }
            }
        });
    }

    // ProviderInstaller callbacks
    @Override public void onProviderInstalled() { /* OK */ }

    @Override
    public void onProviderInstallFailed(int errorCode, android.content.Intent intent) {
        // Not fatal; old devices may still work with the manual TLS 1.2 socket factory above
        GoogleApiAvailability.getInstance().showErrorNotification(this, errorCode);
    }
}
