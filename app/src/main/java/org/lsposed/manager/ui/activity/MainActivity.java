/*
 * <!--This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors-->
 */

package org.lsposed.manager.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.gson.JsonParser;

import org.lsposed.manager.BuildConfig;
import org.lsposed.manager.ConfigManager;
import org.lsposed.manager.NavGraphDirections;
import org.lsposed.manager.R;
import org.lsposed.manager.databinding.ActivityMainBinding;
import org.lsposed.manager.repo.RepoLoader;
import org.lsposed.manager.ui.activity.base.BaseActivity;
import org.lsposed.manager.util.DoHDNS;
import org.lsposed.manager.util.theme.ThemeUtil;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import rikka.material.app.DayNightDelegate;


public class MainActivity extends BaseActivity {
    private static final String KEY_PREFIX = MainActivity.class.getName() + '.';
    private static final String EXTRA_SAVED_INSTANCE_STATE = KEY_PREFIX + "SAVED_INSTANCE_STATE";
    private boolean restarting;
    private ActivityMainBinding binding;

    public static final String TAG = "LSPosedManager";
    @SuppressLint("StaticFieldLeak")
    private static MainActivity instance = null;
    private static OkHttpClient okHttpClient;
    private static Cache okHttpCache;


    public static MainActivity getInstance() {
        return instance;
    }

    @NonNull
    public static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder().cache(getOkHttpCache());
            builder.addInterceptor(chain -> {
                var request = chain.request().newBuilder();
                request.header("User-Agent", TAG);
                return chain.proceed(request.build());
            });
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            if (BuildConfig.DEBUG) builder.addInterceptor(log);
            okHttpClient = builder.dns(new DoHDNS(builder.build())).build();
        }
        return okHttpClient;
    }

    @NonNull
    private static Cache getOkHttpCache() {
        if (okHttpCache == null) {
            okHttpCache = new Cache(new File(MainActivity.getInstance().getCacheDir(), "http_cache"), 50L * 1024L * 1024L);
        }
        return okHttpCache;
    }

    private void loadRemoteVersion() {
        var request = new Request.Builder()
                .url("https://api.github.com/repos/LSPosed/LSPosed/releases/latest")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build();
        var callback = new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) return;
                var body = response.body();
                if (body == null) return;
                try {
                    var info = JsonParser.parseReader(body.charStream()).getAsJsonObject();
                    var name = info.getAsJsonArray("assets").get(0).getAsJsonObject().get("name").getAsString();
                    var code = Integer.parseInt(name.split("-", 4)[2]);
                    var now = Instant.now().getEpochSecond();
                    getPreferences().edit()
                            .putInt("latest_version", code)
                            .putLong("latest_check", now)
                            .putBoolean("checked", true)
                            .apply();
                } catch (Throwable t) {
                    Log.e(MainActivity.TAG, t.getMessage(), t);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(MainActivity.TAG, "loadRemoteVersion", e);
                if (getPreferences().getBoolean("checked", false)) return;
                getPreferences().edit().putBoolean("checked", true).apply();
            }
        };
        getOkHttpClient().newCall(request).enqueue(callback);
    }

    public static boolean needUpdate() {
        var pref = getPreferences();
        if (!pref.getBoolean("checked", false)) return false;
        var now = Instant.now();
        var buildTime = Instant.ofEpochSecond(BuildConfig.BUILD_TIME);
        var check = pref.getLong("latest_check", 0);
        if (check > 0) {
            var checkTime = Instant.ofEpochSecond(check);
            if (checkTime.atOffset(ZoneOffset.UTC).plusDays(30).toInstant().isBefore(now))
                return true;
            var code = pref.getInt("latest_version", 0);
            return code > BuildConfig.VERSION_CODE;
        }
        return buildTime.atOffset(ZoneOffset.UTC).plusDays(30).toInstant().isBefore(now);
    }
    @NonNull
    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, MainActivity.class);
    }

    @NonNull
    private static Intent newIntent(@NonNull Bundle savedInstanceState, @NonNull Context context) {
        return newIntent(context)
                .putExtra(EXTRA_SAVED_INSTANCE_STATE, savedInstanceState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            savedInstanceState = getIntent().getBundleExtra(EXTRA_SAVED_INSTANCE_STATE);
        }
        super.onCreate(savedInstanceState);

        instance = this;

        DayNightDelegate.setApplicationContext(this);
        DayNightDelegate.setDefaultNightMode(ThemeUtil.getDarkTheme());

        loadRemoteVersion();
        RepoLoader.getInstance().loadRemoteData();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            return;
        }
        NavController navController = navHostFragment.getNavController();
        if (binding.homeFragment != null) {
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.main_fragment) {
                    binding.navHostFragment.setVisibility(View.GONE);
                } else {
                    binding.navHostFragment.setVisibility(View.VISIBLE);
                }
            });
        }
        if (intent.getAction() != null && intent.getAction().equals("android.intent.action.APPLICATION_PREFERENCES")) {
            navController.navigate(R.id.action_settings_fragment);
        } else if (intent.hasExtra("modulePackageName") && ConfigManager.isBinderAlive()) {
            navController.navigate(NavGraphDirections.actionAppListFragment(intent.getStringExtra("modulePackageName"), intent.getIntExtra("moduleUserId", -1)));
        } else if (!TextUtils.isEmpty(intent.getDataString())) {
            switch (intent.getDataString()) {
                case "modules":
                    if (!ConfigManager.isBinderAlive()) break;
                    navController.navigate(R.id.action_modules_fragment);
                    break;
                case "logs":
                    if (!ConfigManager.isBinderAlive()) break;
                    navController.navigate(R.id.action_logs_fragment);
                    break;
                case "repo":
                    if (!ConfigManager.isBinderAlive() && !ConfigManager.isMagiskInstalled()) break;
                    navController.navigate(R.id.action_repo_fragment);
                    break;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    public void restart() {
        Bundle savedInstanceState = new Bundle();
        onSaveInstanceState(savedInstanceState);
        finish();
        startActivity(newIntent(savedInstanceState, this));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        restarting = true;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return restarting || super.dispatchKeyEvent(event);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyShortcutEvent(@NonNull KeyEvent event) {
        return restarting || super.dispatchKeyShortcutEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        return restarting || super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(@NonNull MotionEvent event) {
        return restarting || super.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
        return restarting || super.dispatchGenericMotionEvent(event);
    }
}
