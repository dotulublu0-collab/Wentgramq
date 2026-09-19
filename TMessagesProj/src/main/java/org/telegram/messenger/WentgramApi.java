package org.telegram.messenger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WentgramApi {

    // Пока сервер работает в Termux на этом же телефоне.
    public static final String SERVER = "http://127.0.0.1:8000";

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String error);
    }

    public static void sendCode(String phone, Callback callback) {
        request("/auth/send-code", phone, null, null, callback);
    }

    public static void devLogin(String phone, Callback callback) {
        request("/auth/dev-login", phone, null, "Wentgram User", callback);
    }

    public static void verifyCode(
            String phone,
            String code,
            String firstName,
            Callback callback
    ) {
        request("/auth/verify", phone, code, firstName, callback);
    }

    private static void request(
            String endpoint,
            String phone,
            String code,
            String firstName,
            Callback callback
    ) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(SERVER + endpoint);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                JSONObject body = new JSONObject();
                body.put("phone", phone);

                if (code != null) {
                    body.put("code", code);
                }

                if (firstName != null) {
                    body.put("first_name", firstName);
                }

                byte[] bytes = body.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }

                int status = connection.getResponseCode();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                status >= 200 && status < 300
                                        ? connection.getInputStream()
                                        : connection.getErrorStream(),
                                StandardCharsets.UTF_8
                        )
                );

                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                reader.close();

                JSONObject json = new JSONObject(result.toString());

                AndroidUtilities.runOnUIThread(() -> {
                    if (status >= 200 && status < 300) {
                        callback.onSuccess(json);
                    } else {
                        callback.onError(json.optString(
                                "error",
                                "HTTP " + status
                        ));
                    }
                });

            } catch (Exception e) {
                FileLog.e(e);

                AndroidUtilities.runOnUIThread(() ->
                        callback.onError(
                                e.getMessage() == null
                                        ? "Connection error"
                                        : e.getMessage()
                        )
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
