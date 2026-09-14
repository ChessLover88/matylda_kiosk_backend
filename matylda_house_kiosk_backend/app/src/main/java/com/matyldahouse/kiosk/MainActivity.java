package com.matyldahouse.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String START_URL =
            "https://chesslover88.github.io/matylda_kiosk/";

    private static final String ALLOWED_HOST = "chesslover88.github.io";
    private static final String ALLOWED_PATH = "/matylda_kiosk";

    private static final String PIN_SALT =
            "MatyldaHouse-kiosk-v1:";

    /*
     * SHA-256 of:
     * PIN_SALT + administrator PIN
     */
    private static final String PIN_HASH =
            "82cac8281ffbbe17b158e236d7b31691d34450eae660913fb21095306a35a631";

    private static final int REQUIRED_TAPS = 7;
    private static final long TAP_TIMEOUT_MS = 4000L;

    private WebView webView;
    private DevicePolicyManager policyManager;
    private ComponentName adminComponent;

    private int tapCount = 0;
    private long firstTapTime = 0L;
    private boolean leavingKiosk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        policyManager = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);

        adminComponent = new ComponentName(
                this,
                KioskDeviceAdminReceiver.class
        );

        configureDeviceOwner();
        createInterface();
        enterFullscreen();
        enterLockTask();

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureDeviceOwner() {
        if (!policyManager.isDeviceOwnerApp(getPackageName())) {
            return;
        }

        try {
            policyManager.setLockTaskPackages(
                    adminComponent,
                    new String[]{getPackageName()}
            );

            policyManager.setStatusBarDisabled(adminComponent, true);
            policyManager.setKeyguardDisabled(adminComponent, true);
        } catch (SecurityException ignored) {
        }
    }

    private void createInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        configureWebView(webView);

        root.addView(
                webView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        View secretTapArea = new View(this);
        secretTapArea.setBackgroundColor(Color.TRANSPARENT);
        secretTapArea.setOnClickListener(view -> registerSecretTap());

        int size = dpToPixels(72);

        FrameLayout.LayoutParams tapParams =
                new FrameLayout.LayoutParams(size, size);

        tapParams.gravity = Gravity.TOP | Gravity.START;
        root.addView(secretTapArea, tapParams);

        setContentView(root);
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);

        view.setLongClickable(false);
        view.setHapticFeedbackEnabled(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setWebChromeClient(new WebChromeClient());

        view.setDownloadListener((url, userAgent, disposition, mimeType, length) ->
                Toast.makeText(
                        this,
                        "Downloads are disabled",
                        Toast.LENGTH_SHORT
                ).show()
        );

        view.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                Uri uri = request.getUrl();

                boolean allowed =
                        "https".equalsIgnoreCase(uri.getScheme())
                                && ALLOWED_HOST.equalsIgnoreCase(uri.getHost())
                                && uri.getPath() != null
                                && uri.getPath().startsWith(ALLOWED_PATH);

                if (allowed) {
                    return false;
                }

                Toast.makeText(
                        MainActivity.this,
                        "External links are blocked",
                        Toast.LENGTH_SHORT
                ).show();

                return true;
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    showOfflinePage();
                }
            }
        });
    }

    private void showOfflinePage() {
        String html =
                "<!doctype html>" +
                        "<html><head>" +
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<style>" +
                        "body{font-family:sans-serif;text-align:center;" +
                        "padding:48px;background:#fff;color:#222}" +
                        "button{font-size:20px;padding:14px 28px;border:0;" +
                        "border-radius:8px;background:#222;color:#fff}" +
                        "</style></head><body>" +
                        "<h2>Connection unavailable</h2>" +
                        "<p>Check Wi-Fi or mobile network.</p>" +
                        "<button onclick=\"location.href='" + START_URL + "'\">" +
                        "Try again</button>" +
                        "</body></html>";

        webView.loadDataWithBaseURL(
                START_URL,
                html,
                "text/html",
                "UTF-8",
                null
        );
    }

    private void registerSecretTap() {
        long now = System.currentTimeMillis();

        if (firstTapTime == 0L || now - firstTapTime > TAP_TIMEOUT_MS) {
            firstTapTime = now;
            tapCount = 1;
        } else {
            tapCount++;
        }

        if (tapCount >= REQUIRED_TAPS) {
            tapCount = 0;
            firstTapTime = 0L;
            showPinDialog();
        }
    }

    private void showPinDialog() {
        EditText input = new EditText(this);

        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        input.setHint("Administrator PIN");
        input.setSingleLine(true);

        int padding = dpToPixels(24);

        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(
                input,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Maintenance access")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Exit kiosk", null)
                .create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            String candidate = input.getText().toString();

                            if (verifyPin(candidate)) {
                                dialog.dismiss();
                                exitKiosk();
                            } else {
                                input.setError("Incorrect PIN");
                                input.setText("");
                            }
                        })
        );

        dialog.show();
    }

    private boolean verifyPin(String candidate) {
        String candidateHash = sha256(PIN_SALT + candidate);

        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                PIN_HASH.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder result = new StringBuilder();

            for (byte item : bytes) {
                result.append(
                        String.format(Locale.US, "%02x", item & 0xff)
                );
            }

            return result.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private void enterLockTask() {
        if (leavingKiosk) {
            return;
        }

        try {
            startLockTask();
        } catch (IllegalStateException ignored) {
        }
    }

    private void exitKiosk() {
        leavingKiosk = true;

        try {
            if (policyManager.isDeviceOwnerApp(getPackageName())) {
                policyManager.setStatusBarDisabled(adminComponent, false);
            }

            stopLockTask();
        } catch (Exception ignored) {
        }

        try {
            Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);
        } catch (Exception ignored) {
        }

        finishAndRemoveTask();
    }

    private void enterFullscreen() {
        View decorView = getWindow().getDecorView();

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller =
                    decorView.getWindowInsetsController();

            if (controller != null) {
                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private int dpToPixels(int dp) {
        return Math.round(
                dp * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!leavingKiosk) {
            enterFullscreen();
            enterLockTask();
        }
    }

    @Override
    public void onBackPressed() {
        // Intentionally disabled in kiosk mode.
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}