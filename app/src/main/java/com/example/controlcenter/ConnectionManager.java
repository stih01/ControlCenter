package com.example.controlcenter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionManager implements TcpClient.OnMessageReceived {
    private static final String TAG = "ConnectionManager";

    // Интерфейс для связи с ViewModel
    public interface ConnectionManagerListener {
        void onMessageReceived(String message);
        void onConnectionStatusChanged(String status);
        void onPeerStatusChanged(String status);
        void onPeerConnected();
        void onPeerDisconnected();
        void onLimitReached();
    }

    private final ConnectionManagerListener listener;
    private TcpClient tcpClient;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // Логика переподключения и heartbeat
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private static final long RECONNECT_DELAY_MS = 5000;
    private Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final int HEARTBEAT_INTERVAL = 30000;
    private String savedIp;
    private int savedPort;

    public ConnectionManager(ConnectionManagerListener listener) {
        this.listener = listener;
    }

    public void startConnection(String ip, int port) {
        this.savedIp = ip;
        this.savedPort = port;

        if (tcpClient != null && tcpClient.isConnected()) return;
        if (tcpClient != null) tcpClient.close();

        listener.onConnectionStatusChanged("Подключение...");

        tcpClient = new TcpClient(this, ip, port);
        backgroundExecutor.execute(() -> tcpClient.connect());
    }

    public void sendCommand(String command) {
        if (tcpClient != null && tcpClient.isConnected()) {
            backgroundExecutor.execute(() -> tcpClient.sendMessage(command));
        }
    }

    public boolean isConnected() {
        return tcpClient != null && tcpClient.isConnected();
    }

    // --- Реализация TcpClient.OnMessageReceived ---

    @Override
    public void messageReceived(String message) {
        listener.onMessageReceived(message);
    }

    @Override
    public void connectionLost() {
        listener.onConnectionStatusChanged("Потеряно");
        listener.onPeerDisconnected(); // Сбрасываем статус пира при потере сервера
        stopHeartbeat();

        // Логика непрерывного переподключения (без флага shouldAttemptReconnect)
        Log.d(TAG, "Соединение потеряно. Планирую переподключение через " + RECONNECT_DELAY_MS + "мс.");
        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
    }

    @Override
    public void connectionEstablished() {
        listener.onConnectionStatusChanged("Установлено");
        heartbeatHandler.post(heartbeatRunnable);
        reconnectHandler.removeCallbacks(reconnectRunnable);
    }

    @Override public void peerDisconnected() {
        listener.onPeerDisconnected();
    }

    @Override
    public void limitReached() {
        listener.onLimitReached();
        // Ничего не делаем здесь, позволяем connectionLost/TcpClient.close() запустить следующий цикл
        Log.d(TAG, "Получен лимит. Будет предпринята новая попытка.");
    }

    @Override public void peerConnected() { listener.onPeerConnected(); }
    public void onPeerStatusChanged(String status) {} // Пустой метод интерфейса

    // --- Логика Heartbeat и Reconnect ---

    private void stopHeartbeat() { heartbeatHandler.removeCallbacksAndMessages(null); }

    private Runnable heartbeatRunnable = new Runnable() {
        @Override public void run() {
            sendCommand("PING");
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };

    private Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Попытка автоматического переподключения...");
            startConnection(savedIp, savedPort);
        }
    };

    public void shutdown() {
        stopHeartbeat();
        reconnectHandler.removeCallbacksAndMessages(null);
        if (tcpClient != null) tcpClient.close();
        if (!backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow();
        }
    }
}
