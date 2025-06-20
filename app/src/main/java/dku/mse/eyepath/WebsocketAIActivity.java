package dku.mse.eyepath;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebsocketAIActivity extends AppCompatActivity {

    private static final String TAG = "CameraWebSocket";
    private WebSocket webSocket;
    private long lastSentTime = 0;
    private final long MIN_INTERVAL = 50; // milliseconds
    private final long MAX_INTERVAL = 100; // milliseconds

    private ExecutorService cameraExecutor;
    private TextView connectionStatusText;
    private TextView safetyStatusText;
    private Button stopButton;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;

    private boolean isConnected = false;
    private boolean isSending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_websocket);

        // UI 요소 초기화
        connectionStatusText = findViewById(R.id.text_connection_status);
        safetyStatusText = findViewById(R.id.text_safety_status);
        stopButton = findViewById(R.id.button_stop);

        // 진동 및 소리 초기화
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        // 중지 버튼 클릭 리스너
        stopButton.setOnClickListener(v -> {
            stopCamera();
            goToDashboard();
        });

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1001);

        cameraExecutor = Executors.newSingleThreadExecutor();
        updateConnectionStatus("서버 연결 중...");
        updateSafetyStatus("대기 중");
        connectWebSocket();
    }

    private void connectWebSocket() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String token = prefs.getString("token", null);

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "인증 토큰이 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String wsUrl = "ws://10.0.20.161:8000/ws/collision?token=" + token;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket connected");
                isConnected = true;
                runOnUiThread(() -> {
                    updateConnectionStatus("전송 중");
                    updateSafetyStatus("안전");
                });
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "Received: " + text);
                try {
                    JSONObject json = new JSONObject(text);
                    if ("result".equals(json.optString("type"))) {
                        int result = json.optInt("result", -1);
                        handleResult(result);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Message parsing failed", e);
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                Log.e(TAG, "WebSocket error", t);
                isConnected = false;
                runOnUiThread(() -> {
                    updateConnectionStatus("연결 실패");
                    updateSafetyStatus("오프라인");
                });
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                isConnected = false;
                runOnUiThread(() -> {
                    updateConnectionStatus("연결 끊어짐");
                    updateSafetyStatus("오프라인");
                });
            }
        });
    }

    private void handleResult(int result) {
        runOnUiThread(() -> {
            if (result == 1) {
                // 위험 상황 감지
                updateSafetyStatus("⚠️ 위험 감지!");

                // 진동 (1초간)
                if (vibrator != null) {
                    vibrator.vibrate(1000);
                }

                // 경고음 (높은 톤)
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000);
                }

                Toast.makeText(this, "위험한 상황이 감지되었습니다!", Toast.LENGTH_SHORT).show();
            } else {
                // 안전 상태 (result == 0)
                updateSafetyStatus("안전");
            }
        });
    }

    private void updateConnectionStatus(String status) {
        if (connectionStatusText != null) {
            connectionStatusText.setText(status);
        }
    }

    private void updateSafetyStatus(String status) {
        if (safetyStatusText != null) {
            safetyStatusText.setText(status);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Size targetResolution = new Size(1280, 720);
                Preview preview = new Preview.Builder()
                        .setTargetResolution(targetResolution)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetResolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastSentTime >= MIN_INTERVAL && isConnected) {
                        Bitmap bitmap = imageProxyToBitmap(image);
                        if (bitmap != null) {
                            String base64 = encodeBitmapToBase64(bitmap);
                            sendFrame(base64);
                            lastSentTime = currentTime;
                        }
                    }
                    image.close();
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                // 카메라가 시작되면 초기 상태를 안전으로 설정 (서버 연결된 경우에만)
                if (isConnected) {
                    updateSafetyStatus("안전");
                }

            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
                updateConnectionStatus("카메라 시작 실패");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void sendFrame(String base64Data) {
        if (webSocket != null && isConnected) {
            String payload = "{\"type\":\"image\", \"data\":\"" + base64Data + "\"}";
            webSocket.send(payload);
        }
    }

    private void stopCamera() {
        // 웹소켓 종료 메시지 전송
        if (webSocket != null && isConnected) {
            String closeMessage = "{\"type\":\"close\", \"message\":\"사용자가 중지했습니다\"}";
            webSocket.send(closeMessage);
            webSocket.close(1000, "User stopped");
        }

        // 카메라 실행자 종료
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        updateConnectionStatus("중지됨");
    }

    private void goToDashboard() {
        Intent intent = new Intent(WebsocketAIActivity.this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, out);
            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Bitmap conversion failed", e);
            return null;
        }
    }

    private String encodeBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);
        byte[] bytes = stream.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCamera();

        // 톤 제너레이터 해제
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            updateConnectionStatus("카메라 권한 거부됨");
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopCamera();
        goToDashboard();
    }
}