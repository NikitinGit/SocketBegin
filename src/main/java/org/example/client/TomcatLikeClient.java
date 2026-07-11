package org.example.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

// Имитирует несколько "пользователей", одновременно открывающих браузер и бьющих
// в org.example.server.TomcatLikeServer — чтобы наглядно увидеть, как пул потоков
// сервера (core/max/queue) разбирает конкурентные запросы по воркерам http-exec-N.
public class TomcatLikeClient {

    private static final String HOST = "localhost";
    private static final int PORT = 8082;
    private static final int REQUEST_COUNT = 8; // сколько "пользователей" бьют по серверу одновременно

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);       // общий старт для всех потоков-клиентов
        CountDownLatch doneGate = new CountDownLatch(REQUEST_COUNT);

        for (int i = 1; i <= REQUEST_COUNT; i++) {
            int requestId = i;
            Thread clientThread = new Thread(() -> {
                try {
                    startGate.await(); // ждём отмашки, чтобы запросы ушли действительно одновременно
                    sendRequest(requestId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            }, "user-" + i);
            clientThread.start();
        }

        System.out.println("[КЛИЕНТ] Одновременно отправляю " + REQUEST_COUNT + " запросов на " + HOST + ":" + PORT);
        startGate.countDown(); // отпускаем все потоки-клиенты разом
        doneGate.await();
        System.out.println("[КЛИЕНТ] Все запросы завершены.");
    }

    private static void sendRequest(int requestId) {
        try (Socket socket = new Socket(HOST, PORT);
             OutputStream out = socket.getOutputStream();
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String requestLine = "GET /hello?user=" + requestId + " HTTP/1.1";
            out.write((requestLine + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Пропускаем заголовки ответа до пустой строки-разделителя, дальше — тело
            boolean headersEnded = false;
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (!headersEnded) {
                    if (line.isEmpty()) headersEnded = true;
                } else {
                    body.append(line).append('\n');
                }
            }

            System.out.println("[user-" + requestId + "] ответ сервера: " + body.toString().trim());

        } catch (IOException e) {
            System.out.println("[user-" + requestId + "] ошибка: " + e.getMessage());
        }
    }
}
