package dku.mse.eyepath;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import android.widget.TextView;


import androidx.appcompat.app.AppCompatActivity;


import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;


public class DashboardActivity extends AppCompatActivity {

    private TextView greetingText, usageText, photoText;
    private Button cameraButton;
    private TextView profileButton;

    private static final String API_URL = "http://10.0.20.167:8080/usage/";
    private static final String USER_API = "http://10.0.20.166:8080/users";
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String TOKEN_KEY = "token";
    private static final int MAX_DURATION = 120;
    private JSONObject userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        greetingText = findViewById(R.id.text_greeting);
        usageText = findViewById(R.id.text_usage);
        photoText = findViewById(R.id.text_photo_count);
        cameraButton = findViewById(R.id.button_camera);
        profileButton = findViewById(R.id.text_profile_link);

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        cameraButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, WebsocketAIActivity.class);
            startActivity(intent);
        });

        fetchUserInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchUserInfo();
    }

    private void fetchUserInfo() {
        new Thread(() -> {
            try {
                Log.d("Dashboard", "🔍 fetchUserInfo() 시작");
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String token = prefs.getString(TOKEN_KEY, null);
                // 로그인 시 저장했다고 가정
                Log.d("Dashboard", "  - token: " + token);
                if (token == null) {
                    Log.e("Dashboard", "❌ token  null");
                    return;
                }

                // get userId
                URL url = new URL(USER_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                String userId;
                int code = conn.getResponseCode();
                if (code == 200) {
                    String result = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    userData = new JSONObject(result);
                    userId = userData.optString("userId");
                }else {
                    return;
                }
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int year = cal.get(java.util.Calendar.YEAR);
                int month = cal.get(java.util.Calendar.MONTH) + 1; // 0-based
                Log.e("Dashboard", "❌ token 또는 userId가 null");


                String requestUrl = API_URL + userId + "?year=" + year + "&month=" + month;
                url = new URL(requestUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int responseCode = conn.getResponseCode();
                Log.d("Dashboard", "🔄 응답 코드: " + responseCode);


                if (responseCode == 200) {
                    Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
                    String result = scanner.hasNext() ? scanner.next() : "";
                    JSONObject json = new JSONObject(result);
                    Log.d("Dashboard", "📦 응답 데이터: " + result);

                    int duration = json.optInt("used_minutes", 0);

                    runOnUiThread(() -> {
                        greetingText.setText("EYEPATH");
                        usageText.setText(duration + "분 / " + MAX_DURATION + "분");
                        Log.d("Dashboard", "✅ UI 업데이트 완료");
                    });
                } else {
                    Log.e("Dashboard", "서버 응답 코드: " + responseCode);
                }
            } catch (Exception e) {
                Log.e("Dashboard", "사용자 정보 가져오기 실패", e);
            }
        }).start();
    }
}