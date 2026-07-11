package org.example.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AsyncTcpServer {
    private static final int PORT = 8081;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[СЕРВЕР] Запущен на порту " + PORT + ". Ожидание клиента...");

            // Ждем подключения (в реальном приложении тут был бы цикл для многих клиентов)
            Socket clientSocket = serverSocket.accept();
            System.out.println("[СЕРВЕР] Клиент подключился: " + clientSocket.getRemoteSocketAddress());

            // ТАКТИКА: Разделяем сокет на два независимых потока

            // 1. Поток для ЧТЕНИЯ данных от клиента
            Thread readerThread = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                    String message;
                    while ((message = in.readLine()) != null) {
                        System.out.println("\n[СЕРВЕР ПОЛУЧИЛ]: " + message);
                        System.out.print("[СЕРВЕР ОТПРАВИТЬ]: "); // Сброс строки для консоли ввода
                    }
                } catch (IOException e) {
                    System.out.println("[СЕРВЕР] Ошибка чтения: " + e.getMessage());
                } finally {
                    closeSocket(clientSocket);
                }
            });

            // 2. Поток для ОТПРАВКИ данных клиенту (через консоль сервера)
            Thread writerThread = new Thread(() -> {
                try (PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                     Scanner scanner = new Scanner(System.in)) {

                    while (!clientSocket.isClosed()) {
                        System.out.print("[СЕРВЕР ОТПРАВИТЬ]: ");
                        if (scanner.hasNextLine()) {
                            String textToSend = scanner.nextLine();
                            out.println(textToSend); // Отправляем в сокет с переносом строки
                        }
                    }
                } catch (IOException e) {
                    System.out.println("[СЕРВЕР] Ошибка записи: " + e.getMessage());
                }
            });

            // Запускаем оба потока одновременно
            readerThread.start();
            writerThread.start();

            // Ждем завершения работы потоков
            readerThread.join();
            writerThread.join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void closeSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[СЕРВЕР] Соединение закрыто.");
        } catch (IOException e) { e.printStackTrace(); }
    }
}

