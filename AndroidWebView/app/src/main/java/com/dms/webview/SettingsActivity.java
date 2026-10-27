package com.dms.webview;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
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

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "URL não pode estar vazia", Toast.LENGTH_SHORT).show();
                return;
            }
            Config.saveUrl(this, url);
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
}
