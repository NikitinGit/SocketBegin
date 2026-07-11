package org.example.client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AsyncGuiClient extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private PrintWriter out;
    private Socket socket;

    public AsyncGuiClient() {
        // Настройка графического интерфейса (GUI)
        setTitle("Чат-Клиент (Многопоточный TCP)");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        inputField = new JTextField();
        inputField.setBackground(Color.WHITE);
        inputField.setForeground(Color.BLACK);
        JButton sendButton = new JButton("Отправить");

        // Обработчик кнопки "Отправить" (Работает в GUI-потоке)
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage()); // Отправка по Enter

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Подключаемся к сети
        connectToServer();
    }

    private void connectToServer() {
        try {
            // Подключаемся к нашему серверу
            socket = new Socket("localhost", 8081);

            // Инициализируем поток вывода для отправки данных
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            chatArea.append("[СИСТЕМА] Подключено к серверу!\n");

            // ФОНОВЫЙ ПОТОК: Запускаем бесконечное чтение от сервера
            // Если делать это в GUI-потоке, интерфейс намертво зависнет
            Thread networkReader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    String message;
                    while ((message = in.readLine()) != null) {
                        // Swing требует обновлять UI только через специальный метод invokeLater
                        String finalMessage = message;
                        SwingUtilities.invokeLater(() -> chatArea.append("[СЕРВЕР]: " + finalMessage + "\n"));
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> chatArea.append("[СИСТЕМА] Соединение потеряно: " + e.getMessage() + "\n"));
                }
            });
            networkReader.start();

        } catch (IOException e) {
            chatArea.append("[ОШИБКА] Не удалось подключиться к серверу.\n");
        }
    }

    // Этот метод вызывается внутри GUI-потока при клике
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && out != null) {
            out.println(text); // Мгновенно выталкиваем текст в TCP-буфер ОС
            chatArea.append("[ВЫ]: " + text + "\n");
            inputField.setText(""); // Очищаем поле ввода
        }
    }

    public static void main(String[] args) {
        // GTK L&F на Linux игнорирует setBackground/setForeground у JTextField,
        // поэтому явно ставим кроссплатформенный Metal L&F, где цвета применяются как заданы
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Запускаем GUI в правильном потоке Swing
        SwingUtilities.invokeLater(() -> {
            new AsyncGuiClient().setVisible(true);
        });
    }
}

