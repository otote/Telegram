package org.telegram.ui.Adapters;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Translate
 * <p>
 * Created by 袁立位 on 2018/1/23 21:16.
 */

public class Translate {
    static final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private static final String TAG = "Translate";
    private static Executor executor = new ThreadPoolExecutor(4, 10, 10, TimeUnit.SECONDS, queue);
    private static Map<String, String> cache = new HashMap<>();
    public static String BaiduFanYi_appid;
    public static String BaiduFanYi_secretKey;
    public static boolean useTranslateSettings;

    static {
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        useTranslateSettings = preferences.getBoolean("translate_msg", false);
        BaiduFanYi_appid = preferences.getString("BaiduFanYi_appid", "");
        BaiduFanYi_secretKey = preferences.getString("BaiduFanYi_secretKey", "");
    }

    public void translate(final View view, final String query, final ITranslateCallback callback) {
        if (!useTranslateSettings) return;

        final String key = String.valueOf(Math.random());
        view.setTag(987654321, key);

        if (query == null || query.length() == 0) return;
        String s = cache.get(Utilities.MD5(query));
        if (s != null) {
            callback.onResult(s);
            return;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                String kk = (String) view.getTag(987654321);
                if (!key.equals(kk)) {
                    Log.i(TAG, "run: skip translae:" + query);
                    return;
                }
                queryTranslate(key, view, query, callback);
            }
        }, 1000);
    }

    private void queryTranslate(final String key, final View view, final String query, final ITranslateCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String salt = String.valueOf(Math.random());
                    String appid = BaiduFanYi_appid;
                    final String secretKey = BaiduFanYi_secretKey;
                    String sign = Utilities.MD5(appid + query + salt + secretKey);
                    String query1 = URLEncoder.encode(query, "utf-8");
                    String postData = "q=" + query1 + "&from=auto&to=zh&appid=" + appid + "&salt=" + salt + "&sign=" + sign;

                    String urlPath = "http://api.fanyi.baidu.com/api/trans/vip/translate";
                    URL url = new URL(urlPath);
                    HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                    httpConn.setDoOutput(true);   //需要输出
                    httpConn.setDoInput(true);   //需要输入
                    httpConn.setUseCaches(false);  //不允许缓存
                    httpConn.setRequestMethod("POST");   //设置POST方式连接
                    httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    httpConn.connect();
                    DataOutputStream dos = new DataOutputStream(httpConn.getOutputStream());
                    dos.writeBytes(postData);
                    dos.flush();
                    dos.close();
                    int resultCode = httpConn.getResponseCode();
                    if (HttpURLConnection.HTTP_OK == resultCode) {
                        final StringBuilder sb = new StringBuilder();
                        String readLine = "";
                        BufferedReader responseReader = new BufferedReader(new InputStreamReader(httpConn.getInputStream(), "UTF-8"));
                        while ((readLine = responseReader.readLine()) != null) {
                            sb.append(readLine).append("\n");
                        }
                        responseReader.close();
                        System.out.println(sb.toString());

                        org.json.JSONObject jsObj = new org.json.JSONObject(sb.toString());
                        org.json.JSONArray jsArr = jsObj.getJSONArray("trans_result");
                        StringBuilder results = new StringBuilder();
                        for (int i = 0; i < jsArr.length(); i++) {
                            org.json.JSONObject jsonObject = jsArr.getJSONObject(i);
                            results.append(jsonObject.getString("dst")).append("\n");
                        }
                        final String result = results.toString().trim();
                        Log.i(TAG, "run: on translate query:" + query);
                        Log.i(TAG, "run: on translate result:" + result);
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                String kk = (String) view.getTag(987654321);
                                if (!key.equals(kk)) return;
                                callback.onResult(result);
                            }
                        });
                        cache.put(Utilities.MD5(query), result);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }

    public interface ITranslateCallback {

        void onResult(String result);
    }
}
