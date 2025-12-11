package com.example.controlcenter;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;

// ViewModel теперь реализует слушателей ОБОИХ классов: ConnectionManagerListener и ImageProcessorListener
public class CommunicationViewModel extends ViewModel
        implements ConnectionManager.ConnectionManagerListener, ImageProcessor.ImageProcessorListener {

    private static final String TAG = "CommViewModel";

    // --- LiveData для обновления UI ---
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>();
    private final MutableLiveData<String> peerStatus = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> newImageBitmap = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<List<String>> cameraDescriptions = new MutableLiveData<>();
    private final MutableLiveData<List<Integer>> cameraIdsList = new MutableLiveData<>();
    private final MutableLiveData<Integer> imageLoadProgress = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isButtonEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isProgressIndeterminate = new MutableLiveData<>();
    private final MutableLiveData<String> imageSizeText = new MutableLiveData<>();

    // --- Экземпляры менеджеров ---
    private final ConnectionManager connectionManager;
    private final ImageProcessor imageProcessor;

    public CommunicationViewModel() {
        isLoading.postValue(false);
        isButtonEnabled.postValue(false);

        connectionManager = new ConnectionManager(this);
        imageProcessor = new ImageProcessor(this);
    }

    // --- Геттеры LiveData ---
    public LiveData<String> getConnectionStatus() { return connectionStatus; }
    public LiveData<String> getPeerStatus() { return peerStatus; }
    public LiveData<Bitmap> getNewImageBitmap() { return newImageBitmap; }
    public LiveData<Integer> getImageLoadProgress() { return imageLoadProgress; }
    public LiveData<List<String>> getCameraDescriptions() { return cameraDescriptions; }
    public LiveData<List<Integer>> getCameraIdsList() { return cameraIdsList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Boolean> getIsButtonEnabled() { return isButtonEnabled; }
    public LiveData<String> getStatusMessages() { return statusMessages; }
    public LiveData<Boolean> getIsProgressIndeterminate() { return isProgressIndeterminate; }
    public LiveData<String> getImageSizeText() { return imageSizeText; }

    // --- Методы UI-взаимодействия ---
    public void startConnection(String ip, int port) {
        connectionManager.startConnection(ip, port);
    }

    public void sendCommand(String command) {
        connectionManager.sendCommand(command);
    }

    public void lockInterfaceBeforeRequest() {
        isButtonEnabled.postValue(false);
        isLoading.postValue(true);
        isProgressIndeterminate.postValue(true);
        resetImage();
    }

    public void initWakeLock(android.content.Context c) {
        imageProcessor.initWakeLock(c);
    }

    public void decodePendingPhoto() {}


    // =====================================================================
    // РЕАЛИЗАЦИЯ ИНТЕРФЕЙСА ConnectionManagerListener (Обратные вызовы от сети)
    // =====================================================================

    @Override
    public void onMessageReceived(String rawMessage) {
        if (rawMessage == null) return;

        final String message = rawMessage.trim();

        // 1. Игнорируем пустые строки и технические сообщения PING/PONG
        // Мы делаем сравнение без учета регистра на всякий случай
        if (message.isEmpty() ||
                message.equalsIgnoreCase("PING") ||
                message.equalsIgnoreCase("PONG")) {
            return;
        }

        Log.d(TAG, "VM rec: " + (message.length() > 30 ? message.substring(0, 30) + "..." : message));

        // 2. Сначала отдаем в ImageProcessor (если это часть картинки)
        if (imageProcessor.processMessage(message)) {
            return;
        }

        // 3. Обработка системных статусов сервера
        if (message.startsWith("SERVER_STATUS:")) {
            // Эти статусы обрабатываются через onPeerConnected() / onPeerDisconnected()
            // в ConnectionManager, но если нужно выводить их в лог - можно оставить.
            // Если НЕ хотите видеть SERVER_STATUS в TextView, добавьте здесь return;
        }
        // 4. Обработка списка камер
        else if (message.contains(" -- ")) {
            parseCameras(message);
        }
        // 5. Все остальное (реальные сообщения) выводим в TextView
        else {
            statusMessages.postValue(message);
        }
    }


    // Реализация всех обязательных методов интерфейса:
  public void onConnectionStatusChanged(String status) { connectionStatus.postValue(status); }
  public void onLimitReached() { connectionStatus.postValue("Лимит"); }

    // ДОБАВЛЕН НЕ ДОСТАЮЩИЙ МЕТОД:
  public void onPeerStatusChanged(String status) {
        // Этот метод можно использовать, если ConnectionManager отправляет общий статус пира,
        // но в нашей текущей реализации ConnectionManager он не вызывается.
        // Мы используем onPeerConnected/Disconnected вместо него.
    }


    public void onPeerConnected() {
        peerStatus.postValue("Подключен");
        isButtonEnabled.postValue(true);
        sendCommand("camList");
    }


    public void onPeerDisconnected() {
        peerStatus.postValue("Отключен");
        isButtonEnabled.postValue(false);
    }


    private void parseCameras(String msg) {
        try {
            String[] parts = msg.split(" -- ");
            if (parts.length == 2) {
                // Правильный парсинг и trim() для каждого элемента массива
                int id = Integer.parseInt(parts[0].trim()); // Исправлено обращение к индексу
                String desc = parts[1].trim();              // Исправлено обращение к индексу

                List<Integer> ids = cameraIdsList.getValue() != null ? cameraIdsList.getValue() : new ArrayList<>();
                List<String> ds = cameraDescriptions.getValue() != null ? cameraDescriptions.getValue() : new ArrayList<>();
                if (!ids.contains(id)) {
                    ids.add(id); ds.add(desc);
                    cameraIdsList.postValue(new ArrayList<>(ids));
                    cameraDescriptions.postValue(new ArrayList<>(ds));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cameras", e);
        }
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        connectionManager.shutdown();
        imageProcessor.shutdown();
    }

    // =====================================================================
    // РЕАЛИЗАЦИЯ ИНТЕРФЕЙСА ImageProcessorListener
    // =====================================================================

    public void onImageDecoded(Bitmap bitmap) { newImageBitmap.postValue(bitmap); }
    public void onProgressUpdate(int progress) { imageLoadProgress.postValue(progress); }
    public void onImageProcessingStart(String sizeText) {
        isLoading.postValue(true);
        isProgressIndeterminate.postValue(false);
        imageSizeText.postValue(sizeText);
    }
    public void onImageProcessingComplete() {
        isLoading.postValue(false);
        isButtonEnabled.postValue(true);
    }
    public void onError(String message) {
        statusMessages.postValue(message);
        onImageProcessingComplete();
    }
    public void resetImage() {
        newImageBitmap.postValue(null);
        imageSizeText.postValue("");
        imageLoadProgress.postValue(0);
    }

}
