/*
 * This file is part of LSPosed.
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
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.activity.base;

import android.app.Application;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.lsposed.manager.BuildConfig;
import org.lsposed.manager.ConfigManager;
import org.lsposed.manager.R;
import org.lsposed.manager.util.NavUtil;
import org.lsposed.manager.util.theme.ThemeUtil;

import java.lang.reflect.Method;
import java.util.Locale;

import rikka.core.res.ResourcesKt;
import rikka.material.app.MaterialActivity;

public class BaseActivity extends MaterialActivity {

    private static SharedPreferences preferences = null;

    private Resources res = null;
    private ApplicationInfo appInfo = null;

    @Override
    public ClassLoader getClassLoader() {
        return BaseActivity.class.getClassLoader();
    }

    @Override
    public Resources getResources() {
        if (res == null && getIntent() != null && getIntent().hasExtra("apk")) {
            try {
                AssetManager am = AssetManager.class.newInstance();
                Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                addAssetPath.setAccessible(true);
                // TODO: may use classpath
                addAssetPath.invoke(am, getIntent().getStringExtra("apk"));
                res = new Resources(am, super.getResources().getDisplayMetrics(), super.getResources().getConfiguration());
            } catch (Throwable e) {
                Log.e("LSPosedManager", "get res", e);
            }

        }
        if (res != null) {
            return res;
        }
        return super.getResources();
    }

    @Override
    public ComponentName getComponentName() {
        if (getIntent() != null && getIntent().hasExtra("apk")) {
            return ComponentName.unflattenFromString("com.android.settings/org.lsposed.manager.ui.activity.MainActivity");
        }
        return super.getComponentName();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        if (appInfo == null && getIntent() != null && getIntent().hasExtra("apk")) {
            var pkgInfo = getPackageManager().getPackageArchiveInfo(getIntent().getStringExtra("apk"), 0);
            appInfo = pkgInfo != null ? pkgInfo.applicationInfo : null;
        }
        if (appInfo != null) {
            return appInfo;
        }
        return super.getApplicationInfo();
    }

    public static SharedPreferences getPreferences() {
        if (preferences == null) {
            try {
                var app = (Application) Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
                preferences = PreferenceManager.getDefaultSharedPreferences(app);
                if ("CN".equals(Locale.getDefault().getCountry())) {
                    if (!preferences.contains("doh")) {
                        preferences.edit().putBoolean("doh", true).apply();
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return preferences;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        // make sure the versions are consistent
        if (BuildConfig.DEBUG) return;
        if (!ConfigManager.isBinderAlive()) return;
        var version = ConfigManager.getXposedVersionName();
        if (BuildConfig.VERSION_NAME.equals(version)) return;
        new AlertDialog.Builder(this)
                .setMessage(BuildConfig.VERSION_NAME.compareTo(version) > 0 ?
                        R.string.outdated_core : R.string.outdated_manager)
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                    NavUtil.startURL(this, getString(R.string.about_source));
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onApplyUserThemeResource(@NonNull Resources.Theme theme, boolean isDecorView) {
        theme.applyStyle(ThemeUtil.getNightThemeStyleRes(this), true);
        theme.applyStyle(ThemeUtil.getColorThemeStyleRes(), true);
    }

    @Override
    public String computeUserThemeKey() {
        return ThemeUtil.getColorTheme() + ThemeUtil.getNightTheme(this);
    }

    @Override
    public void onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars();
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);

        window.getDecorView().post(() -> {
            if (window.getDecorView().getRootWindowInsets().getSystemWindowInsetBottom() >= Resources.getSystem().getDisplayMetrics().density * 40) {
                window.setNavigationBarColor(ResourcesKt.resolveColor(getTheme(), android.R.attr.navigationBarColor) & 0x00ffffff | -0x20000000);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.setNavigationBarContrastEnforced(false);
                }
            } else {
                window.setNavigationBarColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.setNavigationBarContrastEnforced(true);
                }
            }
        });

    }
}
