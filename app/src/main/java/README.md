## 📸 1. **카메라 권한 및 초기화**

```java
ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1001);
```

* 앱 실행 시 카메라 권한을 요청합니다.
* 권한이 수락되면 `startCamera()` 메서드가 카메라를 초기화합니다.

---

## 🔌 2. **WebSocket 연결**

```java
connectWebSocket();
```

* OkHttp의 `WebSocket` 클라이언트를 사용하여 `ws://10.0.20.161:8000/ws/collision?token=YOUR_TOKEN` 주소에 연결합니다.
* `onOpen()`, `onMessage()`, `onFailure()` 등을 통해 연결 상태 및 메시지를 처리합니다.

---

## 📷 3. **CameraX로 프레임 캡처**

```java
ProcessCameraProvider.getInstance(this)
```

* `CameraX`의 핵심 API인 `ProcessCameraProvider`를 통해 카메라 라이프사이클에 맞춰 바인딩합니다.
* `ImageAnalysis`를 사용하여 프레임을 분석합니다.

```java
imageAnalysis.setAnalyzer(cameraExecutor, image -> {
    ...
});
```

* 프레임 분석기는 백그라운드 스레드에서 동작하며, `image`는 실시간 프레임입니다.

---

## ⏱️ 4. **프레임 전송 주기 제어**

```java
if (currentTime - lastSentTime >= MIN_INTERVAL)
```

* 프레임이 너무 자주 전송되지 않도록 최소 간격(0.05초 = 50ms)을 유지합니다.
* `lastSentTime`을 기록해 다음 전송까지 대기하게 합니다.

---

## 🎨 5. **ImageProxy → Bitmap 변환**

```java
Bitmap bitmap = imageProxyToBitmap(image);
```

* `CameraX`의 `ImageProxy`는 YUV420 포맷입니다.
* 이를 **NV21 바이트 배열로 변환한 후**, `YuvImage`를 통해 JPEG 압축하여 `Bitmap`으로 변환합니다:

```java
YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
yuvImage.compressToJpeg(...);
```

---

## 🧬 6. **Bitmap → Base64 변환**

```java
String base64 = encodeBitmapToBase64(bitmap);
```

* `Bitmap`을 JPEG 형식으로 압축한 후, `Base64.encodeToString()`으로 인코딩합니다.
* 줄 바꿈 없이 (`NO_WRAP`) 전송하기 좋게 만듭니다.

---

## 📡 7. **WebSocket으로 전송**

```java
String payload = "{\"type\":\"frame\", \"data\":\"" + base64Data + "\"}";
webSocket.send(payload);
```

* 프레임 데이터는 JSON 형태로 서버에 전송됩니다.
* `type: "frame"`, `data: "<base64 string>"` 형태입니다.

---

## 🧹 8. **종료 처리**

```java
@Override
protected void onDestroy() {
    ...
}
```

* 액티비티 종료 시:

    * WebSocket 연결을 닫고
    * 카메라 쓰레드 풀도 종료합니다

---

## ✅ 정리: 전체 흐름

1. 권한 요청
2. 카메라 시작 (CameraX + ImageAnalysis)
3. 프레임을 주기적으로 분석 (0.05초 간격)
4. JPEG로 압축 → Base64 인코딩
5. WebSocket으로 JSON 전송

