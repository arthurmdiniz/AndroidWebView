package com.dms.webview;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String[] SITE_NAMES = {
        "LinxDMSConfiguracao", "LinxDMSFabrica", "LinxDMSOficina",
        "LinxDMSPrincipal", "LinxDMSRelatorios", "LinxDMSVeiculos",
        "LinxDMSVendas", "LinxDMSVendasPecas", "Mobile", "Autoshop"
    };

    private static final String HELP_ASSET = "file:///android_asset/help.html";

    static String helpUrl() {
        return HELP_ASSET + "?build=" + BuildConfig.BUILD_TIME;
    }

    private static List<WebViewTab> allTabs = new ArrayList<>();
    private static List<MainActivity> activeInstances = new ArrayList<>();

    private LinearLayout tabBar;
    private FrameLayout container;
    private ProgressBar progressBar;
    private List<WebViewTab> tabs;
    private List<View> tabViews;
    private int selectedTab = -1;

    public static void injectNetworkLogToAllTabs() {
        for (MainActivity act : activeInstances) {
            for (WebViewTab tab : act.tabs) {
                tab.injectNetworkScript();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLogger.init(this);
        PdfHandler.cleanTmp();
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.CAMERA }, 1002);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("");

        tabBar = findViewById(R.id.tab_bar);
        container = findViewById(R.id.webview_container);
        progressBar = findViewById(R.id.progress_bar);
        tabs = new ArrayList<>();
        tabViews = new ArrayList<>();

        activeInstances.add(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String url = Config.loadUrl(this);
            addTab(url != null ? url : helpUrl());
            handleIntent(getIntent());
        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activeInstances.remove(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (WebViewTab.handlePermissionResult(requestCode, permissions, grantResults))
            return;
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1003) {
            WebViewTab.handleFileChooserResult(resultCode, data);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (tabs != null) handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            String url = intent.getDataString();
            if (url != null) addTab(url);
        }
    }

    void addTab(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")
                && !url.startsWith("data:") && !url.startsWith("file:///android_asset/")) {
            url = "https://" + url;
        }

        int index = tabs.size();
        WebViewTab tab = new WebViewTab(this, url);
        tabs.add(tab);
        allTabs.add(tab);

        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setPadding(12, 0, 4, 0);
        tabLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText("Carregando...");
        tv.setPadding(0, 12, 4, 12);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(null, Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnClose = new TextView(this);
        btnClose.setText("\u2715");
        btnClose.setTextSize(12);
        btnClose.setTextColor(Color.parseColor("#88FFFFFF"));
        btnClose.setPadding(8, 8, 8, 8);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        tabLayout.addView(tv);
        tabLayout.addView(btnClose);

        tabLayout.setOnClickListener(v -> selectTab(index));

        btnClose.setOnClickListener(v -> {
            if (tabs.size() <= 1) {
                Toast.makeText(this, "Mantenha ao menos uma aba", Toast.LENGTH_SHORT).show();
                return;
            }
            closeTab(index);
        });

        tabBar.addView(tabLayout);
        tabViews.add(tabLayout);

        tab.setTitleChangeListener(title -> {
            int idx = tabs.indexOf(tab);
            if (idx >= 0 && idx < tabViews.size()) {
                String display = (title == null || title.isEmpty()) ? "Sem t\u00edtulo"
                    : title.length() > 20 ? title.substring(0, 20) + "\u2026" : title;
                ((TextView)((LinearLayout)tabViews.get(idx)).getChildAt(0)).setText(display);
            }
        });

        tab.setLoadListener(new WebViewTab.LoadListener() {
            @Override
            public void onPageStarted() {
                runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
            }
            @Override
            public void onPageFinished() {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
            @Override
            public void onError(String description) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this,
                        "Erro: " + description, Toast.LENGTH_LONG).show();
                });
            }
        });

        selectTab(index);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        selectedTab = index;

        for (int i = 0; i < tabViews.size(); i++) {
            View v = tabViews.get(i);
            if (i == index) {
                v.setBackgroundColor(Color.parseColor("#5C2E80"));
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        container.removeAllViews();
        WebView wv = tabs.get(index).getWebView();
        if (wv.getParent() != null)
            ((ViewGroup) wv.getParent()).removeView(wv);
        container.addView(wv, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        wv.requestFocus();
    }

    private void closeTab(int index) {
        if (tabs.size() <= 1 || index < 0 || index >= tabs.size()) return;

        WebViewTab removed = tabs.remove(index);
        allTabs.remove(removed);
        tabBar.removeView(tabViews.remove(index));
        removed.destroy();
        container.removeAllViews();

        int oldSelected = selectedTab;
        selectedTab = -1;
        int newPos = Math.min(index == oldSelected ? index : oldSelected, tabs.size() - 1);
        if (tabs.size() > 0 && index <= oldSelected) {
            selectTab(newPos);
        } else if (tabs.size() > 0) {
            selectTab(oldSelected);
        }
    }

    void navigateToSite(String siteName) {
        if (selectedTab < 0 || selectedTab >= tabs.size()) {
            addTab(Config.DEFAULT_URL);
            return;
        }
        String currentUrl = tabs.get(selectedTab).getCurrentUrl();
        if (currentUrl == null
                || !(currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))) {
            currentUrl = Config.loadUrl(this);
        }
        if (currentUrl == null) {
            addTab(Config.DEFAULT_URL);
            return;
        }
        String newUrl = replaceSiteSegment(currentUrl, siteName);
        if (newUrl != null) addTab(newUrl);
    }

    private String replaceSiteSegment(String url, String newSite) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) return null;
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                String basePath = path.substring(0, lastSlash);
                return uri.getScheme() + "://" + uri.getAuthority() + basePath + "/" + newSite;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem sitesItem = menu.findItem(R.id.menu_sites);
        if (sitesItem != null && sitesItem.getSubMenu() != null) {
            for (String name : SITE_NAMES) {
                sitesItem.getSubMenu().add(name).setOnMenuItemClickListener(item -> {
                    navigateToSite(name);
                    return true;
                });
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_tab) {
            String url = Config.loadUrl(this);
            addTab(url != null ? url : Config.DEFAULT_URL);
            return true;
        }
        if (id == R.id.menu_help) {
            addTab(helpUrl());
            return true;
        }
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.menu_version) {
            String versionName = "1.0";
            try {
                var pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                versionName = pInfo.versionName;
            } catch (Exception ignored) {}
            String build = BuildConfig.BUILD_TIME;
            new AlertDialog.Builder(this)
                    .setTitle("Versão")
                    .setMessage("Versão: " + versionName + "\nBuild: " + build)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Verificar atualização", (d, w) -> UpdateChecker.check(this))
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (selectedTab >= 0 && selectedTab < tabs.size()) {
            WebView wv = tabs.get(selectedTab).getWebView();
            if (wv.canGoBack()) {
                wv.goBack();
                return;
            }
        }
        super.onBackPressed();
    }
}
