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
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

/**
 * MainActivity - Android应用主Activity
 * 
 * 功能说明：
 * 1. 使用WebView加载并显示本地网页资源（从assets/dist目录）
 * 2. 通过虚拟域名"mypage.test"拦截请求，将网络请求映射到本地资源文件
 * 3. 支持Service Worker请求拦截，确保离线功能正常工作
 * 4. 支持网页文件上传功能（通过文件选择器）
 * 5. 支持WebView全屏显示功能（如视频全屏播放）
 * 6. 全屏时自动旋转屏幕为横屏，退出全屏时恢复原始方向
 */
public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> mUploadCallback; // 文件上传回调，用于接收用户选择的文件
    private static final int FILECHOOSER_RESULTCODE = 100; // 文件选择器的请求码

    // 全屏相关成员变量
    private View customView; // 全屏时显示的自定义视图（通常是视频播放器）
    private WebChromeClient.CustomViewCallback customViewCallback; // 全屏视图的回调，用于通知WebView全屏状态
    private FrameLayout fullscreenContainer; // 全屏视图的容器，用于承载全屏内容
    private int originalOrientation; // 保存进入全屏前的屏幕方向，退出全屏时恢复

    // 将资源加载器提取为成员变量，供 WebViewClient 和 ServiceWorkerClient 共用
    private AssetResourceLoader assetLoader;

    /**
     * Activity创建时的初始化方法
     * 
     * 主要工作：
     * 1. 设置无标题栏窗口
     * 2. 初始化WebView并配置基本设置（JavaScript、本地存储、文件访问等）
     * 3. 设置WebViewClient拦截页面请求，将虚拟域名的请求映射到本地资源
     * 4. 设置ServiceWorkerClient拦截Service Worker请求（API 24+）
     * 5. 设置WebChromeClient处理文件选择和全屏功能
     * 6. 加载初始网页
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // 隐藏标题栏

        // 保存当前屏幕方向，用于全屏退出时恢复
        originalOrientation = getRequestedOrientation();

        // 初始化资源加载帮助类
        // 参数说明：虚拟域名"mypage.test"，本地资源目录"dist"（位于assets/dist）
        assetLoader = new AssetResourceLoader(this, "mypage.test", "dist");

        // 创建并配置WebView
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); // 启用JavaScript支持
        settings.setDomStorageEnabled(true); // 启用DOM存储（LocalStorage等）
        settings.setAllowFileAccess(true); // 允许访问本地文件
        //WebView.setWebContentsDebuggingEnabled(true); // 启用远程调试（Chrome DevTools）

        // 1. 设置常规 WebViewClient (拦截页面请求)
        // 当WebView加载网页资源时（HTML、CSS、JS、图片等），会调用此方法
        // 我们拦截虚拟域名"mypage.test"的请求，将其映射到assets/dist目录下的本地文件
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 调用资源加载器拦截请求，如果是虚拟域名则返回本地资源，否则返回null走默认网络逻辑
                return assetLoader.shouldIntercept(request.getUrl());
            }
        });

        // 2. 【关键】设置 ServiceWorkerClient (拦截 Service Worker 请求)
        // Service Worker是网页的离线缓存机制，它也会发起网络请求
        // 如果不拦截Service Worker的请求，离线功能可能无法正常工作
        // ServiceWorkerController 仅在 API 24 (Android 7.0) 及以上可用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    // 复用同样的加载逻辑，确保Service Worker也能访问本地资源
                    return assetLoader.shouldIntercept(request.getUrl());
                }
            });
        }

        // 3. 设置 WebChromeClient (处理文件选择和全屏)
        // WebChromeClient用于处理WebView的扩展功能，如文件选择、全屏、进度条等
        webView.setWebChromeClient(new WebChromeClient() {
            /**
             * 处理网页中的文件选择请求（如<input type="file">）
             * 当网页需要用户选择文件时，会调用此方法
             * 我们启动系统的文件选择器，让用户选择文件，然后将结果返回给网页
             */
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // 如果之前有未完成的文件选择，先取消它
                if (mUploadCallback != null) mUploadCallback.onReceiveValue(null);
                mUploadCallback = filePathCallback; // 保存回调，等待用户选择文件后调用
                
                // 创建文件选择Intent
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); // 只选择可打开的文件
                intent.setType("*/*"); // 允许选择所有类型的文件
                
                // 启动文件选择器
                // 注意：startActivityForResult() 在 API 30+ 已弃用，但为了兼容性保留
                // 如果需要完全使用新API，需要引入 ActivityX 库并使用 ActivityResultLauncher
                startActivityForResult(Intent.createChooser(intent, "File Browser"), FILECHOOSER_RESULTCODE);
                return true; // 返回true表示我们处理了这个请求
            }

            /**
             * 处理全屏显示请求（如视频全屏播放）
             * 当网页中的视频请求全屏时，会调用此方法
             * 
             * 实现步骤：
             * 1. 创建全屏容器并设置黑色背景
             * 2. 将全屏视图（通常是视频播放器）添加到容器中
             * 3. 将容器添加到Activity的根视图，覆盖整个屏幕
             * 4. 隐藏系统UI（状态栏、导航栏）实现沉浸式全屏
             * 5. 设置屏幕方向为横屏，让视频占满整个屏幕
             */
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                // 如果已经有一个全屏视图，先关闭它（防止重复全屏）
                if (customView != null) {
                    onHideCustomView();
                    return;
                }

                // 保存全屏视图和回调，用于后续退出全屏
                customView = view;
                customViewCallback = callback;

                // 创建全屏容器，用于承载全屏内容
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setBackgroundColor(0xFF000000); // 黑色背景

                // 将全屏视图（视频播放器）添加到容器中
                fullscreenContainer.addView(view);

                // 将容器添加到Activity的根视图（DecorView），覆盖整个屏幕
                ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
                decorView.addView(fullscreenContainer);

                // 隐藏系统UI（状态栏、导航栏），实现真正的沉浸式全屏
                hideSystemUI();

                // 设置屏幕方向为横屏，允许根据设备方向自动旋转（支持正反横屏）
                // 这样视频可以占满整个屏幕，而不是只占据中间部分
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            /**
             * 处理退出全屏请求
             * 当网页中的全屏内容（如视频）需要退出全屏时，会调用此方法
             */
            @Override
            public void onHideCustomView() {
                exitFullscreen(); // 调用统一的退出全屏方法
            }
        });

        setContentView(webView);
        webView.loadUrl("https://mypage.test/index.html");
    }

    /**
     * 处理返回键按下事件
     * 
     * 处理逻辑：
     * 1. 如果当前正在全屏，先退出全屏
     * 2. 如果WebView有历史记录可以返回，则返回上一页
     * 3. 否则执行默认的返回行为（退出Activity）
     * 
     * 注意：onBackPressed() 在 API 33+ 已弃用，但为了兼容性保留
     * 如果需要完全使用新API，需要引入 ActivityX 库并使用 OnBackPressedDispatcher
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // 如果正在全屏，先退出全屏（而不是直接退出Activity）
        if (customView != null) {
            exitFullscreen();
            return;
        }

        // 否则处理WebView的返回（如果有历史记录）
        if (webView != null && webView.canGoBack()) {
            webView.goBack(); // 返回WebView的上一页
        } else {
            // 没有历史记录，执行默认返回行为（退出Activity）
            super.onBackPressed();
        }
    }

    /**
     * 退出全屏的辅助方法
     * 
     * 执行步骤：
     * 1. 恢复系统UI（显示状态栏和导航栏）
     * 2. 恢复进入全屏前的屏幕方向
     * 3. 从Activity根视图中移除全屏容器
     * 4. 通知WebView全屏已关闭
     * 5. 清空所有全屏相关的引用
     */
    private void exitFullscreen() {
        if (customView == null) {
            return; // 如果不在全屏状态，直接返回
        }

        // 恢复系统UI（状态栏、导航栏）
        showSystemUI();

        // 恢复进入全屏前保存的屏幕方向
        setRequestedOrientation(originalOrientation);

        // 从Activity的根视图（DecorView）中移除全屏容器
        ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
        decorView.removeView(fullscreenContainer);

        // 调用回调通知WebView全屏已关闭，让网页知道全屏状态已改变
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }

        // 清空所有全屏相关的引用，释放资源
        customView = null;
        customViewCallback = null;
        fullscreenContainer = null;
    }

    /**
     * 隐藏系统UI，实现沉浸式全屏效果
     * 
     * 根据Android版本使用不同的API：
     * - API 30+ (Android 11+): 使用新的 WindowInsetsController API
     * - API 19-29 (Android 4.4-10): 使用 setSystemUiVisibility API
     * - API < 19: 使用 Window flags（功能有限）
     * 
     * 效果：隐藏状态栏和导航栏，让内容占满整个屏幕
     */
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ 使用新的 WindowInsetsController（推荐方式）
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars()); // 隐藏系统栏
                // 设置行为：用户可以通过滑动边缘临时显示系统栏
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19-29 使用 setSystemUiVisibility（已弃用但兼容旧版本）
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE // 保持布局稳定
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // 布局延伸到导航栏下方
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // 布局延伸到状态栏下方
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // 隐藏导航栏
                | View.SYSTEM_UI_FLAG_FULLSCREEN // 隐藏状态栏
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); // 沉浸式模式，滑动后自动隐藏
        } else {
            // API < 19 使用 Window flags（功能有限，只能隐藏状态栏）
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /**
     * 显示系统UI，退出全屏效果
     * 
     * 根据Android版本使用不同的API恢复系统UI：
     * - API 30+: 使用 WindowInsetsController.show()
     * - API 19-29: 使用 setSystemUiVisibility() 设置基础标志
     * - API < 19: 清除全屏标志
     * 
     * 效果：恢复显示状态栏和导航栏
     */
    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ 使用新的 WindowInsetsController（推荐方式）
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars()); // 显示系统栏
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19-29 使用 setSystemUiVisibility（已弃用但兼容旧版本）
            View decorView = getWindow().getDecorView();
            // 只保留布局标志，移除隐藏标志，让系统栏显示出来
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else {
            // API < 19 使用 Window flags
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /**
     * 处理Activity结果回调（用于文件选择器）
     * 
     * 当用户从文件选择器中选择文件后，系统会调用此方法
     * 我们将用户选择的文件URI通过回调返回给WebView，让网页可以访问文件
     * 
     * 注意：onActivityResult() 在 API 30+ 已弃用，但为了兼容性保留
     * 如果需要完全使用新API，需要引入 ActivityX 库并使用 ActivityResultLauncher
     */
    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
            // 处理文件选择器的结果
            if (mUploadCallback == null) return; // 如果没有回调，直接返回
            
            Uri[] results = null;
            // 如果用户成功选择了文件，获取文件的URI
            // 使用 getData() 替代已弃用的 getDataString()
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()}; // 将URI包装成数组
            }
            // 将结果通过回调返回给WebView
            mUploadCallback.onReceiveValue(results);
            mUploadCallback = null; // 清空回调，防止重复使用
        } else {
            // 其他请求码，交给父类处理
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 资源加载器类 (Helper Class)
     * 
     * 功能说明：
     * 将网络请求拦截并映射到本地资源文件的逻辑封装在此类中
     * 通过虚拟域名（如"mypage.test"）拦截请求，从assets目录加载对应的本地文件
     * 支持WebViewClient和ServiceWorkerClient共用，避免代码重复
     * 
     * 工作流程：
     * 1. 检查请求的域名是否匹配虚拟域名
     * 2. 将URL路径转换为assets目录下的文件路径
     * 3. 从assets目录读取文件并返回WebResourceResponse
     * 4. 根据文件扩展名设置正确的MIME类型
     */
    private static class AssetResourceLoader {
        private Activity context; // Activity上下文，用于访问assets
        private String virtualDomain; // 虚拟域名（如"mypage.test"）
        private String localAssetBase; // 本地资源基础目录（如"dist"）
        private Map<String, String> mimeTypes; // MIME类型映射表

        /**
         * 构造函数
         * 
         * @param context Activity上下文
         * @param domain 虚拟域名，用于拦截请求
         * @param assetBase assets目录下的基础文件夹名称
         */
        public AssetResourceLoader(Activity context, String domain, String assetBase) {
            this.context = context;
            this.virtualDomain = domain;
            this.localAssetBase = assetBase;
            initMimeTypes(); // 初始化MIME类型映射表
        }

        /**
         * 统一的拦截入口
         * 
         * 处理流程：
         * 1. 检查URL的域名是否匹配虚拟域名
         * 2. 将URL路径转换为assets目录下的文件路径
         * 3. 从assets目录读取文件
         * 4. 根据文件扩展名确定MIME类型
         * 5. 返回WebResourceResponse给WebView
         * 
         * @param url 请求的URL
         * @return WebResourceResponse 如果匹配虚拟域名则返回本地资源，否则返回null走默认网络逻辑
         */
        public WebResourceResponse shouldIntercept(Uri url) {
            // 仅仅拦截我们的虚拟域名，其他域名走默认网络逻辑
            if (url.getHost() != null && url.getHost().equals(virtualDomain)) {
                String assetPath = "";
                try {
                    // 获取URL路径，处理默认路径和空路径
                    String path = url.getPath();
                    if (path == null || path.equals("/") || path.isEmpty()) {
                        path = "/index.html"; // 默认加载index.html
                    }
                    // 移除路径开头的"/"，因为assets路径不需要前导斜杠
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    // 构建完整的assets路径：dist/xxx.html
                    assetPath = localAssetBase + "/" + path;

                    // 从assets目录打开文件流
                    InputStream stream = context.getAssets().open(assetPath);
                    // 根据文件扩展名获取MIME类型
                    String mimeType = getMimeType(assetPath);
                    // 返回资源响应，WebView会使用这个响应来渲染内容
                    return new WebResourceResponse(mimeType, "UTF-8", stream);

                } catch (IOException e) {
                    // 文件不存在，返回404错误页面
                    Log.e("WebViewDebug", "File not found: " + assetPath);
                    String errorHtml = "<html><body><h2 style='color:red;'>404 Not Found</h2><p>" + assetPath + "</p></body></html>";
                    return new WebResourceResponse("text/html", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(errorHtml.getBytes()));
                }
            }
            // 如果不是该域名的请求，返回 null 让 WebView 走默认网络逻辑
            // 注意：因为虚拟域名不存在，网络请求会失败，但这是预期的行为
            return null;
        }

        /**
         * 初始化MIME类型映射表
         * 根据文件扩展名映射到对应的MIME类型，用于正确设置HTTP响应头
         */
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

        /**
         * 根据文件URL获取MIME类型
         * 
         * @param url 文件URL或路径
         * @return MIME类型字符串，如果无法识别则返回"application/octet-stream"
         */
        private String getMimeType(String url) {
            // 从URL中提取文件扩展名
            String extension = "";
            int i = url.lastIndexOf('.');
            if (i > 0) extension = url.substring(i + 1);
            
            // 从映射表中查找MIME类型
            String mime = mimeTypes.get(extension);
            // 如果找不到，返回默认的二进制流类型
            return mime != null ? mime : "application/octet-stream";
        }
    }
}