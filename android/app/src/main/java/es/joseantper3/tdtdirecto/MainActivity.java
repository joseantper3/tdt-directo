package es.joseantper3.tdtdirecto;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * TDT Directo para Android y Android TV.
 * Carga la web publicada (siempre la última versión) y, sin conexión, la copia incluida en el APK.
 * Los canales que solo se ven en su web oficial se abren en el navegador del dispositivo.
 */
public class MainActivity extends Activity {
    static final String HOME = "https://joseantper3.github.io/tdt-directo/";
    static final String OFFLINE = "file:///android_asset/index.html";

    FrameLayout root;
    WebView web;
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    WebChromeClient chrome;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xFF07090E);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        hideBars();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        // Puente para que la página abra fuera los canales que no se dejan ver dentro.
        web.addJavascriptInterface(new Bridge(), "TDTAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                Uri u = r.getUrl();
                String h = u.getHost();
                if ("file".equals(u.getScheme())) return false;
                if (h != null && h.equals("joseantper3.github.io")) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                // Sin conexión con la web: usa la copia incluida en la app.
                if (r.isForMainFrame() && !OFFLINE.equals(v.getUrl())) v.loadUrl(OFFLINE);
            }
        });

        chrome = new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean user, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                        openExternal(r.getUrl());
                        w.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
                msg.sendToTarget();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
                hideBars();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCb != null) customCb.onCustomViewHidden();
                customCb = null;
            }
        };
        web.setWebChromeClient(chrome);

        if (state != null) web.restoreState(state);
        else web.loadUrl(HOME);
    }

    class Bridge {
        @JavascriptInterface
        public void open(String url) {
            runOnUiThread(() -> openExternal(Uri.parse(url)));
        }
    }

    void openExternal(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "No hay ningún navegador instalado para abrir este canal", Toast.LENGTH_LONG).show();
        }
    }

    // Teclas del mando que el navegador no traduce: se envían a la página como las del teclado.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && customView == null) {
            String k = null;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    k = "ChannelUp"; break;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    k = "ChannelDown"; break;
                case KeyEvent.KEYCODE_GUIDE:
                case KeyEvent.KEYCODE_MENU:
                    k = "g"; break;
                case KeyEvent.KEYCODE_INFO:
                    k = "i"; break;
            }
            if (k != null) {
                web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'" + k + "',bubbles:true}))", null);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // Atrás: cierra la guía o el editor; si no hay nada abierto, sale de la app.
    @Override
    public void onBackPressed() {
        if (customView != null) { chrome.onHideCustomView(); return; }
        web.evaluateJavascript(
            "(function(){var o=document.querySelector('.layer.on');if(o){document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return 1}return 0})()",
            r -> { if (!"1".equals(r)) finish(); });
    }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
