package com.example.controlcenter;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpClient implements Runnable {

    private static final String TAG = "TcpClient";
    private final String SERVER_IP;
    private final int SERVER_PORT;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private OnMessageReceived messageListener = null;
    private volatile boolean running = false;
    private boolean connected = false;

    public TcpClient(OnMessageReceived listener, String ip, int port) {
        this.messageListener = listener;
        this.SERVER_IP = ip;
        this.SERVER_PORT = port;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed() && connected;
    }

    public void connect() {
        if (!running) {
            running = true;
            executorService.submit(this);
        }
    }

    @Override
    public void run() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), 5000);

            socket.setKeepAlive(true);
            socket.setSoTimeout(5000);

            connected = true;
            Log.d(TAG, "Подключено к серверу: " + SERVER_IP + ":" + SERVER_PORT);
            if (messageListener != null) {
                messageListener.connectionEstablished();
            }

            output = new PrintWriter(socket.getOutputStream(), true);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (running) {
                String message = null;
                try {
                    message = input.readLine();
                } catch (SocketTimeoutException e) {
                    continue;
                }

                if (message == null) {
                    throw new IOException("Соединение потеряно сервером (получен EOF)");
                }

                if (message.trim().isEmpty()) {
                    continue;
                }

                // ВНИМАНИЕ: Исправленная логика уведомлений
                if (messageListener != null) {
                    Log.d(TAG, "DEBUG: Read line from socket: " + message);

                    // Сначала вызываем специфические события интерфейса
                    if (message.startsWith("SERVER_STATUS: PEER_CONNECTED")) {
                        messageListener.peerConnected();
                    } else if (message.startsWith("SERVER_STATUS: PEER_DISCONNECTED")) {
                        messageListener.peerDisconnected();
                    } else if (message.startsWith("SERVER_ERROR: CONNECTION_LIMIT_REACHED")) {
                        Log.w(TAG, "Сервер отклонил подключение: лимит клиентов.");
                        messageListener.limitReached();
                        //break; // Только здесь выходим из цикла
                    }

                    // ЗАТЕМ ОБЯЗАТЕЛЬНО отправляем всё сообщение в messageReceived
                    // Именно этот метод слушает ваша ViewModel для обновления статусов
                    messageListener.messageReceived(message);
                }
            }

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Таймаут подключения или чтения", e);
            if (messageListener != null) messageListener.connectionLost();
        } catch (SocketException e) {
            Log.e(TAG, "Ошибка сокета", e);
            if (messageListener != null) messageListener.connectionLost();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка TCP ввода/вывода", e);
            if (messageListener != null) messageListener.connectionLost();
        } catch (Exception e) {
            Log.e(TAG, "Неизвестная ошибка TCP", e);
            if (messageListener != null) messageListener.connectionLost();
        } finally {
            close();
        }
    }

    public synchronized void sendMessage(String message) {
        if (output != null && !output.checkError()) {
            output.println(message);
            output.flush();
        }
    }

    public void close() {
        running = false;
        connected = false;
        if (socket != null) {
            try {
                socket.close();
                Log.d(TAG, "Соединение закрыто.");
            } catch (IOException e) {
                Log.e(TAG, "Ошибка при закрытии сокета: ", e);
            }
        }
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    public interface OnMessageReceived {
        void messageReceived(String message);

        void connectionLost();

        void connectionEstablished();

        void peerDisconnected();

        void limitReached();

        void peerConnected();
    }
}
