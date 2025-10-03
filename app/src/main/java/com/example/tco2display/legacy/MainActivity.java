package com.example.tco2display.legacy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ProviderInstaller.ProviderInstallListener {

    private SevenSegmentView segView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private IntanglesRepository repo;
    private static final long REFRESH_MS = 2000L; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        segView = findViewById(R.id.segView);

        // Install/upgrade security provider for TLS 1.2 (needed on API 18)
        try {
            ProviderInstaller.installIfNeededAsync(this, this);
        } catch (Exception ignored) {}

        // OkHttp 3.12.x + Retrofit 2.9
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(log)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://apis.intangles.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        repo = new IntanglesRepository(retrofit.create(IntanglesApi.class));
        tick();
    }

    private void tick() {
        io.submit(new Runnable() {
            @Override public void run() {
                try {
                    double tco2 = repo.fetchAndSumTco2(
                            BuildConfig.INTANGLES_TOKEN,
                            "962759605811675136",                     // accId
                            "966986020958502912,969208267156750336",  // specIds
                            300, "en", true, "total_fuel_consumed",
                            "", true, "kg", 0.45
                    );
                    ui.post(new Runnable() { @Override public void run() { segView.setTco2(tco2); }});
                } catch (Exception e) {
                    // Keep last value; you could add a small indicator here if needed
                } finally {
                    ui.postDelayed(new Runnable() { @Override public void run() { tick(); }}, REFRESH_MS);
                }
            }
        });
    }

    // TLS provider callbacks
    @Override public void onProviderInstalled() { /* OK */ }
    @Override public void onProviderInstallFailed(int errorCode, android.content.Intent intent) {
        GoogleApiAvailability.getInstance().showErrorNotification(this, errorCode);
    }
}
