package carcasson.client;

import carcasson.controller.AppController;
import javax.swing.*;
import java.awt.*;


public class ChatPanel extends JPanel {
    private AppController controller;
    private JTextArea chatArea;
    private JTextField messageField;

    public ChatPanel(AppController controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Чат игры"));
        setPreferredSize(new Dimension(350, 250));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());

        JButton sendButton = new JButton("Отпр.");
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        appendMessage("ДОБРО ПОЖАЛОВАТЬ В КАРКАССОН");
        appendMessage("Правила подсчета очков:");
        appendMessage("- Город: 2 очка за каждую плитку города");
        appendMessage("- Дорога: 1 очко за каждую плитку дороги");
        appendMessage("- Монастырь: 1 очко + по 1 за каждого соседа");
        appendMessage("");
        appendMessage("Правила установки миплов:");
        appendMessage("- Мипл можно ставить только на свою последнюю плитку");
        appendMessage("- Нельзя ставить мипла на поле");
        appendMessage("- Если объект (дорога/город) уже занят другим игроком,");
        appendMessage("  нельзя поставить на него мипла");
        appendMessage("- У каждого игрока 4 мипла");
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty()) {
            controller.sendChatMessage(text);
            messageField.setText("");
        }
        messageField.requestFocus();
    }

    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
}