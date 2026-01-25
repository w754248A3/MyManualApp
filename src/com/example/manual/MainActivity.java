package com.example.manual;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build; // 需要导入 Build
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ServiceWorkerClient; // 新增导入
import android.webkit.ServiceWorkerController; // 新增导入
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> mUploadCallback;
    private static final int FILECHOOSER_RESULTCODE = 100;

    // 全屏相关成员变量
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;
    private int originalOrientation; // 保存原始屏幕方向

    // 将资源加载器提取为成员变量，供 WebViewClient 和 ServiceWorkerClient 共用
    private AssetResourceLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 保存当前屏幕方向
        originalOrientation = getRequestedOrientation();

        // 初始化资源加载帮助类 (配置域名和文件夹)
        assetLoader = new AssetResourceLoader(this, "mypage.test", "dist");

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        WebView.setWebContentsDebuggingEnabled(true);

        // 1. 设置常规 WebViewClient (拦截页面请求)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 调用提取出来的加载逻辑
                return assetLoader.shouldIntercept(request.getUrl());
            }
        });

        // 2. 【关键】设置 ServiceWorkerClient (拦截 Service Worker 请求)
        // ServiceWorkerController 仅在 API 24 (Android 7.0) 及以上可用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    // 复用同样的加载逻辑！
                    return assetLoader.shouldIntercept(request.getUrl());
                }
            });
        }

        // 3. 设置 WebChromeClient (处理文件选择和全屏)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadCallback != null) mUploadCallback.onReceiveValue(null);
                mUploadCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "File Browser"), FILECHOOSER_RESULTCODE);
                return true;
            }

            // 处理全屏显示（如全屏播放视频）
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                // 如果已经有一个全屏视图，先关闭它
                if (customView != null) {
                    onHideCustomView();
                    return;
                }

                // 保存全屏视图和回调
                customView = view;
                customViewCallback = callback;

                // 创建全屏容器
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setBackgroundColor(0xFF000000);

                // 将全屏视图添加到容器中
                fullscreenContainer.addView(view);

                // 将容器添加到Activity的根视图
                ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
                decorView.addView(fullscreenContainer);

                // 隐藏系统UI，实现真正的全屏
                hideSystemUI();

                // 设置屏幕方向为横屏（允许自动旋转）
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            // 处理退出全屏
            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        setContentView(webView);
        webView.loadUrl("https://mypage.test/index.html");
    }

    @Override
    public void onBackPressed() {
        // 如果正在全屏，先退出全屏
        if (customView != null) {
            exitFullscreen();
            return;
        }

        // 否则处理WebView的返回
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // 退出全屏的辅助方法
    private void exitFullscreen() {
        if (customView == null) {
            return;
        }

        // 恢复系统UI
        showSystemUI();

        // 恢复原始屏幕方向
        setRequestedOrientation(originalOrientation);

        // 从根视图中移除全屏容器
        ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
        decorView.removeView(fullscreenContainer);

        // 调用回调通知WebView全屏已关闭
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }

        // 清空引用
        customView = null;
        customViewCallback = null;
        fullscreenContainer = null;
    }

    // 隐藏系统UI，实现全屏效果
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    // 显示系统UI，退出全屏效果
    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (mUploadCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getDataString() != null) {
                results = new Uri[]{Uri.parse(data.getDataString())};
            }
            mUploadCallback.onReceiveValue(results);
            mUploadCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // --- 提取出的资源加载类 (Helper Class) ---
    // 将之前的逻辑封装在这里，避免重复代码
    private static class AssetResourceLoader {
        private Activity context;
        private String virtualDomain;
        private String localAssetBase;
        private Map<String, String> mimeTypes;

        public AssetResourceLoader(Activity context, String domain, String assetBase) {
            this.context = context;
            this.virtualDomain = domain;
            this.localAssetBase = assetBase;
            initMimeTypes();
        }

        // 统一的拦截入口
        public WebResourceResponse shouldIntercept(Uri url) {
            // 仅仅拦截我们的虚拟域名
            if (url.getHost() != null && url.getHost().equals(virtualDomain)) {
                String assetPath = "";
                try {
                    String path = url.getPath();
                    if (path == null || path.equals("/") || path.isEmpty()) {
                        path = "/index.html";
                    }
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    assetPath = localAssetBase + "/" + path;

                    InputStream stream = context.getAssets().open(assetPath);
                    String mimeType = getMimeType(assetPath);
                    return new WebResourceResponse(mimeType, "UTF-8", stream);

                } catch (IOException e) {
                    Log.e("WebViewDebug", "File not found: " + assetPath);
                    String errorHtml = "<html><body><h2 style='color:red;'>404 Not Found</h2><p>" + assetPath + "</p></body></html>";
                    return new WebResourceResponse("text/html", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(errorHtml.getBytes()));
                }
            }
            // 如果不是该域名的请求，返回 null 让 WebView 走默认网络逻辑 (如果 Service Worker 也是 null，它会走网络，但因为域名是假的会失败)
            return null;
        }

        private void initMimeTypes() {
            mimeTypes = new HashMap<>();
            mimeTypes.put("html", "text/html");
            mimeTypes.put("css", "text/css");
            mimeTypes.put("js", "application/javascript");
            mimeTypes.put("json", "application/json");
            mimeTypes.put("png", "image/png");
            mimeTypes.put("jpg", "image/jpeg");
            mimeTypes.put("svg", "image/svg+xml");
        }

        private String getMimeType(String url) {
            String extension = "";
            int i = url.lastIndexOf('.');
            if (i > 0) extension = url.substring(i + 1);
            String mime = mimeTypes.get(extension);
            return mime != null ? mime : "application/octet-stream";
        }
    }
}