package dku.mse.eyepath;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ProfileActivity extends AppCompatActivity {
    private View viewMode, editMode;
    private ImageView avatarImage;
    private TextView nameView, userIdView, emailView, phoneView, ageView, heightView;
    private EditText phoneEdit, ageEdit, heightEdit;
    private Button editButton, saveButton;

    private static final String USER_API = "http://10.0.20.166:8080/users";
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String TOKEN_KEY = "token";

    private JSONObject userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_profile_combined);

        viewMode = findViewById(R.id.view_mode);
        editMode = findViewById(R.id.edit_mode);

        avatarImage = findViewById(R.id.avatar_image);
        nameView = findViewById(R.id.text_name);
        userIdView = findViewById(R.id.text_userid);
        emailView = findViewById(R.id.text_email);
        phoneView = findViewById(R.id.text_phone);
        ageView = findViewById(R.id.text_age);
        heightView = findViewById(R.id.text_height);

        phoneEdit = findViewById(R.id.edit_phone);
        ageEdit = findViewById(R.id.edit_age);
        heightEdit = findViewById(R.id.edit_height);

        editButton = findViewById(R.id.button_edit_profile);
        saveButton = findViewById(R.id.button_save);

        editButton.setOnClickListener(v -> switchToEditMode());
        saveButton.setOnClickListener(v -> saveUserInfo());

        fetchUserInfo();
    }

    private void switchToEditMode() {
        viewMode.setVisibility(View.GONE);
        editMode.setVisibility(View.VISIBLE);

        phoneEdit.setText(phoneView.getText());
        ageEdit.setText(ageView.getText().toString().replace("세", ""));
        heightEdit.setText(heightView.getText().toString().replace("cm", ""));
    }

    private void switchToViewMode() {
        viewMode.setVisibility(View.VISIBLE);
        editMode.setVisibility(View.GONE);
    }

    private void fetchUserInfo() {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String token = prefs.getString(TOKEN_KEY, null);
                if (token == null) return;

                URL url = new URL(USER_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int code = conn.getResponseCode();
                if (code == 200) {
                    String result = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    userData = new JSONObject(result);

                    runOnUiThread(() -> {
                        nameView.setText(userData.optString("name"));
                        userIdView.setText("@" + userData.optString("userId"));
                        emailView.setText(userData.optString("email"));
                        phoneView.setText(userData.optString("phoneNum"));
                        ageView.setText(userData.optInt("age") + "세");
                        heightView.setText(userData.optInt("userHeight") + "cm");
                        avatarImage.setImageResource(R.drawable.hocha);
                    });
                }
            } catch (Exception e) {
                Log.e("ProfileActivity", "회원 정보 불러오기 실패", e);
            }
        }).start();
    }

    private void saveUserInfo() {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String token = prefs.getString(TOKEN_KEY, null);
                if (token == null || userData == null) return;

                JSONObject body = new JSONObject();
                body.put("name", userData.optString("name"));
                body.put("email", userData.optString("email"));
                body.put("phoneNum", phoneEdit.getText().toString());
                body.put("age", Integer.parseInt(ageEdit.getText().toString()));
                body.put("userHeight", Integer.parseInt(heightEdit.getText().toString()));

                URL url = new URL(USER_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200) {
                        switchToViewMode();
                        fetchUserInfo();
                        new AlertDialog.Builder(this)
                                .setTitle("성공")
                                .setMessage("회원 정보가 저장되었습니다.")
                                .setPositiveButton("확인", null)
                                .show();
                    } else {
                        Toast.makeText(this, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("ProfileActivity", "저장 오류", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "서버 오류 발생", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
