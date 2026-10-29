package com.dms.webview;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private RadioGroup rgProxyMode;
    private EditText proxyHost;
    private EditText proxyPort;
    private TextView tvProxyStatus;
    private Button btnProxyDetect;
    private Button btnProxyTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EditText urlInput = findViewById(R.id.url_input);
        String currentUrl = Config.loadUrl(this);
        urlInput.setText(currentUrl != null ? currentUrl : Config.PREDEFINED_URL);

        CheckBox chkSideCapture = findViewById(R.id.chk_side_capture);
        chkSideCapture.setChecked(Config.isSideCaptureButtonEnabled(this));
        chkSideCapture.setOnCheckedChangeListener((buttonView, isChecked) ->
            Config.setSideCaptureButtonEnabled(this, isChecked));

        RadioGroup rgPhotoQuality = findViewById(R.id.rg_photo_quality);
        int quality = Config.getPhotoQuality(this);
        rgPhotoQuality.check(quality == Config.PHOTO_QUALITY_LOW ? R.id.rb_quality_low
            : quality == Config.PHOTO_QUALITY_MEDIUM ? R.id.rb_quality_medium
            : R.id.rb_quality_high);
        rgPhotoQuality.setOnCheckedChangeListener((group, checkedId) -> {
            int selected = checkedId == R.id.rb_quality_low ? Config.PHOTO_QUALITY_LOW
                : checkedId == R.id.rb_quality_medium ? Config.PHOTO_QUALITY_MEDIUM
                : Config.PHOTO_QUALITY_HIGH;
            Config.setPhotoQuality(this, selected);
        });

        TextView tvEngineInfo = findViewById(R.id.tv_camera_engine_info);
        RadioGroup rgEngine = findViewById(R.id.rg_camera_engine);
        RadioButton rbLegacy = findViewById(R.id.rb_engine_legacy);
        RadioButton rbCamera2 = findViewById(R.id.rb_engine_camera2);
        int level = CameraEngine.hardwareLevel(this);
        boolean supported = CameraEngine.isSupported(this);
        boolean recommended = CameraEngine.isRecommended(this);
        tvEngineInfo.setText("Nível de hardware: " + CameraEngine.levelName(level) + ". " + (recommended
            ? "Motor moderno recomendado para este aparelho."
            : supported
                ? "Motor moderno disponível, com recursos limitados."
                : "Motor moderno indisponível neste aparelho; use o legado."));
        rbLegacy.setText(recommended ? "Legado" : "Legado (recomendado)");
        rbCamera2.setEnabled(supported);
        rbCamera2.setText(!supported ? "Moderno (indisponível)"
            : recommended ? "Moderno (recomendado)" : "Moderno (Camera2)");
        rgEngine.check(Config.isCamera2Enabled(this) && supported
            ? R.id.rb_engine_camera2 : R.id.rb_engine_legacy);
        rgEngine.setOnCheckedChangeListener((group, checkedId) ->
            Config.setCamera2Enabled(this, checkedId == R.id.rb_engine_camera2));

        CheckBox chkLogging = findViewById(R.id.chk_network_logging);
        chkLogging.setChecked(Config.isNetworkLoggingEnabled(this));

        chkLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Config.setNetworkLoggingEnabled(this, isChecked);
            NetworkLogger.setEnabled(isChecked);
            if (isChecked) {
                MainActivity.injectNetworkLogToAllTabs();
            }
            Toast.makeText(this,
                isChecked ? "Log de rede ativado" : "Log de rede desativado",
                Toast.LENGTH_SHORT).show();
        });

        setupProxySection();

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "URL não pode estar vazia", Toast.LENGTH_SHORT).show();
                return;
            }
            String mode = selectedProxyMode();
            if (Config.MODE_MANUAL.equals(mode)) {
                String host = proxyHost.getText().toString().trim();
                int port = parsePort(proxyPort.getText().toString());
                if (host.isEmpty() || port < 1) {
                    Toast.makeText(this, "Informe servidor e porta válidos", Toast.LENGTH_SHORT).show();
                    return;
                }
                Config.setProxyHost(this, host);
                Config.setProxyPort(this, port);
            }
            Config.setProxyMode(this, mode);
            Config.setProxyBypassLocalEnabled(this,
                ((CheckBox) findViewById(R.id.chk_proxy_bypass)).isChecked());

            Config.saveUrl(this, url);
            ProxyManager.init(this);
            ProxyManager.applyToWebView(this, MainActivity::reloadProxyTabs);
            Toast.makeText(this, "URL salva. Recarregue o app.", Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Limpar configuração")
                .setMessage("Resetar para a URL padrão?")
                .setPositiveButton("Sim", (d, w) -> {
                    Config.clear(this);
                    urlInput.setText(Config.PREDEFINED_URL);
                    Toast.makeText(this, "Configuração resetada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Não", null)
                .show();
        });
    }

    private void setupProxySection() {
        rgProxyMode = findViewById(R.id.rg_proxy_mode);
        proxyHost = findViewById(R.id.proxy_host);
        proxyPort = findViewById(R.id.proxy_port);
        tvProxyStatus = findViewById(R.id.tv_proxy_status);
        btnProxyDetect = findViewById(R.id.btn_proxy_detect);
        btnProxyTest = findViewById(R.id.btn_proxy_test);
        CheckBox chkProxyBypass = findViewById(R.id.chk_proxy_bypass);

        String mode = Config.getProxyMode(this);
        rgProxyMode.check(Config.MODE_MANUAL.equals(mode) ? R.id.rb_proxy_manual
            : Config.MODE_OFF.equals(mode) ? R.id.rb_proxy_off
            : R.id.rb_proxy_auto);
        chkProxyBypass.setChecked(Config.isProxyBypassLocalEnabled(this));
        tvProxyStatus.setText(ProxyManager.status());
        showProxyFields(mode);

        rgProxyMode.setOnCheckedChangeListener((group, checkedId) -> showProxyFields(selectedProxyMode()));

        btnProxyDetect.setOnClickListener(v -> {
            String selected = selectedProxyMode();
            Config.setProxyMode(this, selected);
            setProxyBusy(true, "Procurando proxy...");
            ProxyManager.discover(this, changed -> {
                setProxyBusy(false, ProxyManager.status());
                if (Config.MODE_MANUAL.equals(selected) && !Config.getProxyCachedHost(this).isEmpty()) {
                    proxyHost.setText(Config.getProxyCachedHost(this));
                    proxyPort.setText(String.valueOf(Config.getProxyCachedPort(this)));
                }
                showProxyFields(selected);
                if (changed) MainActivity.reloadProxyTabs();
            });
        });

        btnProxyTest.setOnClickListener(v -> {
            setProxyBusy(true, "Testando conexão...");
            new Thread(() -> {
                String result = ProxyManager.testConnection();
                runOnUiThread(() -> setProxyBusy(false, "Teste: " + result));
            }).start();
        });
    }

    private void showProxyFields(String mode) {
        boolean manual = Config.MODE_MANUAL.equals(mode);
        if (Config.MODE_AUTO.equals(mode)) {
            String cachedHost = Config.getProxyCachedHost(this);
            proxyHost.setText(cachedHost);
            proxyPort.setText(cachedHost.isEmpty() ? String.valueOf(Config.DEFAULT_PROXY_PORT)
                : String.valueOf(Config.getProxyCachedPort(this)));
        } else if (manual && proxyHost.getText().toString().trim().isEmpty()) {
            proxyHost.setText(Config.getProxyHost(this));
            proxyPort.setText(String.valueOf(Config.getProxyPort(this)));
        }
        proxyHost.setEnabled(manual);
        proxyPort.setEnabled(manual);
    }

    private void setProxyBusy(boolean busy, String message) {
        btnProxyDetect.setEnabled(!busy);
        btnProxyTest.setEnabled(!busy);
        tvProxyStatus.setText(message);
    }

    private String selectedProxyMode() {
        int checked = rgProxyMode.getCheckedRadioButtonId();
        return checked == R.id.rb_proxy_manual ? Config.MODE_MANUAL
            : checked == R.id.rb_proxy_off ? Config.MODE_OFF
            : Config.MODE_AUTO;
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
