package com.example.controlcenter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.PowerManager;
import android.util.Base64;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageProcessor {

    private static final String TAG = "ImageProcessor";
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;

    private boolean receivingImageMode = false;
    private StringBuffer base64ImageBuffer = new StringBuffer();
    private int expectedImageSizeChars = 0;
    private int currentReceivedChars = 0;

    private final ImageProcessorListener listener;

    // Интерфейс для обратной связи с ViewModel
    public interface ImageProcessorListener {
        void onImageDecoded(Bitmap bitmap);

        void onProgressUpdate(int progress);

        void onImageProcessingStart(String sizeText);

        void onImageProcessingComplete();

        void onError(String message);
    }

    public ImageProcessor(ImageProcessorListener listener) {
        this.listener = listener;
    }

    public void initWakeLock(android.content.Context context) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(android.content.Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::TransferLock");
        }
    }

    // Основной метод для обработки входящих строк
    public boolean processMessage(String message) {
        if (message == null || message.isEmpty()) return false;

        // 1. Обработка начала передачи
        if (message.contains("SIZE:") && !receivingImageMode) {
            try {
                String sizePart = message.substring(message.indexOf(":") + 1).trim();
                // На всякий случай убираем нецифровые символы, если они приклеились
                sizePart = sizePart.replaceAll("[^0-9]", "");

                expectedImageSizeChars = Integer.parseInt(sizePart);
                currentReceivedChars = 0;
                base64ImageBuffer.setLength(0);

                acquireWakeLock();
                receivingImageMode = true;

                int sizeInKb = (int) ((expectedImageSizeChars * 0.75) / 1024);
                listener.onImageProcessingStart("Размер: ~" + sizeInKb + " КБ");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing size: " + message, e);
                return false;
            }
        }

        // 2. Обработка данных внутри режима приема
        if (receivingImageMode) {
            // Проверяем, не содержит ли эта строка маркер конца
            if (message.contains("END123")) {
                // Отрезаем "END123" и всё, что после него, забираем только данные до маркера
                String dataBeforeEnd = message.substring(0, message.indexOf("END123"));
                if (!dataBeforeEnd.isEmpty() && !dataBeforeEnd.equals("IMAGE")) {
                    base64ImageBuffer.append(dataBeforeEnd);
                }

                Log.d(TAG, ">>> Маркер END123 найден. Итого символов: " + base64ImageBuffer.length());
                decodeReceivedImageAsync();
                return true;
            }

            // Пропускаем служебное слово, если оно пришло отдельной строкой
            if (message.equals("IMAGE")) return true;

            // Добавляем строку в буфер
            base64ImageBuffer.append(message);
            currentReceivedChars += message.length();

            // Обновляем прогресс
            if (expectedImageSizeChars > 0) {
                int p = (int) ((currentReceivedChars * 100.0) / expectedImageSizeChars);
                listener.onProgressUpdate(Math.min(p, 99));
            }
            return true;
        }

        return false;
    }


    private void decodeReceivedImageAsync() {
        if (base64ImageBuffer.length() == 0) {
            cleanup();
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                byte[] decoded = Base64.decode(base64ImageBuffer.toString(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);

                if (bitmap != null) {
                    listener.onImageDecoded(bitmap);
                } else {
                    listener.onError("Не удалось декодировать изображение.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Критическая ошибка при декодировании: ", e);
                listener.onError("Ошибка декодирования: " + e.getMessage());
            } finally {
                cleanup();
            }
        });
    }

    private void cleanup() {
        base64ImageBuffer.setLength(0);
        receivingImageMode = false;
        releaseWakeLock();
        listener.onImageProcessingComplete();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    public void shutdown() {
        if (!backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow();
        }
    }
}
