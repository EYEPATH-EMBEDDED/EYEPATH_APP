package dku.mse.eyepath;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LoginActivity extends AppCompatActivity {
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;

    private static final String LOGIN_URL = "http://10.0.20.166:8080/auth/login";
    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String TOKEN_KEY = "token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.input_email);
        passwordInput = findViewById(R.id.input_password);
        loginButton = findViewById(R.id.btn_login);

        loginButton.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일과 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(LOGIN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("userId", email);
                jsonBody.put("password", password);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    String response = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    JSONObject jsonResponse = new JSONObject(response);
                    String token = jsonResponse.optString("accessToken");

                    if (!token.isEmpty()) {
                        // 토큰 저장
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putString(TOKEN_KEY, token).apply();
                        prefs.edit().putString("userId", email);
                        runOnUiThread(() -> {
                            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        showError("로그인에 실패했습니다");
                    }
                } else {
                    showError("로그인에 실패했습니다");
                }

            } catch (Exception e) {
                Log.e(TAG, "서버 오류", e);
                showError("서버 오류가 발생했습니다");
            }
        }).start();
    }

    private void showError(String msg) {
        runOnUiThread(() -> Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show());
    }
}
