package com.borrowbox.kuet.networking;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.borrowbox.kuet.utils.AppConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final int TIMEOUT_MS = 10000;

    private static ApiClient instance;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public void checkHealth(ApiCallback<JSONObject> callback) {
        getJson("/health", null, callback);
    }

    public void register(JSONObject payload, ApiCallback<JSONObject> callback) {
        postJson("/auth/register", payload, null, callback);
    }

    public void login(String identifier, String password, ApiCallback<JSONObject> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("identifier", identifier);
            body.put("password", password);
            postJson("/auth/login", body, null, callback);
        } catch (JSONException e) {
            callback.onError("JSON error: " + e.getMessage());
        }
    }

    public void sendCode(String token, String email, ApiCallback<JSONObject> callback) {
        try {
            JSONObject body = new JSONObject();
            if (email != null && !email.isEmpty()) {
                body.put("email", email);
            }
            postJson("/auth/send-code", body, token, callback);
        } catch (JSONException e) {
            callback.onError("JSON error: " + e.getMessage());
        }
    }

    public void verifyCode(String token, String code, String email, ApiCallback<JSONObject> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("code", code);
            if (email != null && !email.isEmpty()) {
                body.put("email", email);
            }
            postJson("/auth/verify-code", body, token, callback);
        } catch (JSONException e) {
            callback.onError("JSON error: " + e.getMessage());
        }
    }

    public void getMe(String token, ApiCallback<JSONObject> callback) {
        getJson("/auth/me", token, callback);
    }

    public void getProducts(String search, String token, ApiCallback<JSONArray> callback) {
        String endpoint = "/products";
        if (search != null && !search.trim().isEmpty()) {
            endpoint += "?search=" + java.net.URLEncoder.encode(search.trim());
        }

        final String finalEndpoint = endpoint;
        executor.execute(() -> {
            try {
                String responseStr = executeHttp("GET", finalEndpoint, null, token);
                JSONObject obj = new JSONObject(responseStr);
                JSONArray products = obj.optJSONArray("products");
                if (products == null) products = new JSONArray();
                final JSONArray finalProducts = products;
                mainHandler.post(() -> callback.onSuccess(finalProducts));
            } catch (Exception e) {
                Log.e(TAG, "getProducts error: " + e.getMessage());
                String friendly = formatNetworkException(e, AppConfig.API_BASE_URL);
                mainHandler.post(() -> callback.onError(friendly));
            }
        });
    }

    public void getProductDetails(String productId, String token, ApiCallback<JSONObject> callback) {
        getJson("/products/" + productId, token, callback);
    }

    public void createRental(String productId, String startDate, String endDate, String token, ApiCallback<JSONObject> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("product_id", productId);
            body.put("start_date", startDate);
            body.put("end_date", endDate);
            postJson("/rentals", body, token, callback);
        } catch (JSONException e) {
            callback.onError("JSON error: " + e.getMessage());
        }
    }

    public void getMyRentals(String token, ApiCallback<JSONObject> callback) {
        getJson("/rentals/mine", token, callback);
    }

    public void postJson(String endpoint, JSONObject payload, String token, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String bodyStr = payload != null ? payload.toString() : null;
                String responseStr = executeHttp("POST", endpoint, bodyStr, token);
                JSONObject obj = new JSONObject(responseStr);
                mainHandler.post(() -> callback.onSuccess(obj));
            } catch (Exception e) {
                Log.e(TAG, "postJson error at " + endpoint + ": " + e.getMessage());
                String friendly = formatNetworkException(e, AppConfig.API_BASE_URL);
                mainHandler.post(() -> callback.onError(friendly));
            }
        });
    }

    public void getJson(String endpoint, String token, ApiCallback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String responseStr = executeHttp("GET", endpoint, null, token);
                JSONObject obj = new JSONObject(responseStr);
                mainHandler.post(() -> callback.onSuccess(obj));
            } catch (Exception e) {
                Log.e(TAG, "getJson error at " + endpoint + ": " + e.getMessage());
                String friendly = formatNetworkException(e, AppConfig.API_BASE_URL);
                mainHandler.post(() -> callback.onError(friendly));
            }
        });
    }

    public static String formatNetworkException(Throwable t, String targetUrl) {
        if (t == null) return "Unknown network error";
        String msg = t.getMessage() != null ? t.getMessage() : "";
        String lower = msg.toLowerCase();

        if (t instanceof java.net.ConnectException || lower.contains("failed to connect") || lower.contains("connection refused") || lower.contains("econnrefused")) {
            return "Cannot connect to BorrowBox server at " + targetUrl + ".\nPlease make sure your backend server is running and your phone and PC are on the same Wi-Fi.";
        }
        if (t instanceof java.net.SocketTimeoutException || lower.contains("timeout") || lower.contains("timed out")) {
            return "Connection to " + targetUrl + " timed out.\nPlease check your PC firewall and Wi-Fi connection.";
        }
        if (t instanceof java.net.UnknownHostException || lower.contains("unable to resolve host") || lower.contains("no address associated")) {
            return "Cannot find server at " + targetUrl + ".\nPlease verify the Server URL in developer settings.";
        }
        if (lower.contains("cleartext") || lower.contains("cleartraffic")) {
            return "Cleartext HTTP traffic blocked for " + targetUrl + ". Check network security settings.";
        }
        return msg;
    }

    private String executeHttp(String method, String endpoint, String jsonBody, String token) throws Exception {
        String fullUrl = AppConfig.API_BASE_URL + endpoint;
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");

        if (token != null && !token.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token.trim());
        }

        if (jsonBody != null && (method.equals("POST") || method.equals("PUT"))) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 400)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        }

        String responseBody = sb.toString();

        if (responseCode >= 200 && responseCode < 300) {
            return responseBody;
        } else {
            String errorMsg = "HTTP " + responseCode;
            try {
                JSONObject errObj = new JSONObject(responseBody);
                if (errObj.has("error")) {
                    errorMsg = errObj.getString("error");
                }
            } catch (Exception ignored) {
                if (!responseBody.isEmpty()) {
                    errorMsg = responseBody;
                }
            }
            throw new Exception(errorMsg);
        }
    }
}
