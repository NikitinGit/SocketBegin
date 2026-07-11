package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Упрощённая модель того, что Tomcat делает под капотом со Spring MVC:
// один Acceptor-поток только принимает соединения, а сама обработка запроса
// уходит в пул воркеров (аналог http-nio-8080-exec-N), где и живёт
// DispatcherServlet -> Controller -> Service.
public class TomcatLikeServer {

    private static final int PORT = 8082;

    // Совпадает по смыслу с ThreadPoolTaskExecutor / server.tomcat.threads.*
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int QUEUE_CAPACITY = 25;

    // Общий "сервис"-бин: единственный экземпляр на всё приложение (как Spring singleton),
    // его обрабатывают конкурентно все потоки пула
    private static final RequestCountingService requestCountingService = new RequestCountingService();

    public static void main(String[] args) throws IOException {
        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory namedThreadFactory = runnable ->
                new Thread(runnable, "http-exec-" + threadCounter.getAndIncrement());

        ThreadPoolExecutor requestPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                60L, TimeUnit.SECONDS,          // столько живёт поток сверх core, пока простаивает
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                namedThreadFactory
        );

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.printf("[TOMCAT-LIKE] Слушаю порт %d (core=%d, max=%d, queue=%d)%n",
                    PORT, CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);

            while (true) {
                // Acceptor-поток: только принимает TCP-соединение, сам запрос не обрабатывает
                Socket clientSocket = serverSocket.accept();

                // Диспетчеризация задачи в пул — аналог того, как Tomcat передаёт
                // запрос свободному worker-потоку
                requestPool.execute(() -> handleRequest(clientSocket));
            }
        }
    }

    // Выполняется на потоке из пула (http-exec-N) — аналог обработки одного HTTP-запроса
    // в DispatcherServlet -> Controller -> Service
    private static void handleRequest(Socket clientSocket) {
        String threadName = Thread.currentThread().getName();
        try (clientSocket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            System.out.println("[" + threadName + "] обрабатываю запрос: " + requestLine);

            // Имитация полезной работы в @Service (например, поход в БД)
            Thread.sleep(300);

            int requestNumber = requestCountingService.handleAndCount();

            String body = "Обработано потоком " + threadName
                    + ", это запрос №" + requestNumber + " через общий singleton-сервис\n";
            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

        } catch (IOException | InterruptedException e) {
            System.out.println("[" + threadName + "] ошибка обработки: " + e.getMessage());
        }
    }

    // Аналог @Service-бина: один экземпляр на всё приложение,
    // к нему параллельно обращаются все потоки пула.
    // AtomicInteger тут принципиален — обычный "private int counter" ловил бы гонку
    // ровно так, как обсуждали (несколько exec-потоков одновременно читают/пишут одно и то же поле).
    private static class RequestCountingService {
        private final AtomicInteger counter = new AtomicInteger(0);

        int handleAndCount() {
            return counter.incrementAndGet();
        }
    }
}
