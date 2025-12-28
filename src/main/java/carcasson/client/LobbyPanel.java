package carcasson.client;

import carcasson.controller.AppController;
import javax.swing.*;
import java.awt.*;

public class LobbyPanel extends JPanel {
    private AppController controller;
    private JTextField nameField;
    private JTextField serverField;

    public LobbyPanel(AppController controller) {
        this.controller = controller;
        setLayout(new BorderLayout());

        try {
            ImageIcon bgIcon = new ImageIcon(getClass().getResource("/background_menu.jpg"));
            JLabel bgLabel = new JLabel(bgIcon);
            bgLabel.setLayout(new GridBagLayout());

            JPanel formPanel = createFormPanel();
            bgLabel.add(formPanel);

            add(bgLabel, BorderLayout.CENTER);
        } catch (Exception e) {
            add(createFormPanel(), BorderLayout.CENTER);
        }
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;



        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;


        gbc.gridy = 1;


        JLabel nameLabel = new JLabel("Имя игрока:");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 16));

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(nameLabel, gbc);

        nameField = new JTextField(20);
        nameField.setFont(new Font("Arial", Font.PLAIN, 16));

        gbc.gridx = 1;
        panel.add(nameField, gbc);

        JLabel serverLabel = new JLabel("Адрес сервера:");
        serverLabel.setForeground(Color.WHITE);
        serverLabel.setFont(new Font("Arial", Font.BOLD, 16));

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(serverLabel, gbc);

        serverField = new JTextField("localhost", 20);
        serverField.setFont(new Font("Arial", Font.PLAIN, 16));

        gbc.gridx = 1;
        panel.add(serverField, gbc);

        JButton connectButton = new JButton("Подключиться к игре");
        connectButton.setFont(new Font("Arial", Font.BOLD, 18));
        connectButton.setBackground(new Color(70, 130, 180));
        connectButton.setForeground(Color.WHITE);
        connectButton.addActionListener(e -> connectToGame());

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(connectButton, gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(30, 10, 10, 10);

        return panel;
    }

    private void connectToGame() {
        String name = nameField.getText().trim();
        String server = serverField.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите имя игрока", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (server.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите адрес сервера", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        controller.connectToServer(name, server);
    }
}