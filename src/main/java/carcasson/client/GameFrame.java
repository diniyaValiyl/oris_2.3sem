package carcasson.client;

import carcasson.controller.AppController;
import javax.swing.*;
import java.awt.*;

public class GameFrame extends JFrame {
    private AppController controller;
    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    public GameFrame(AppController controller) {
        this.controller = controller;

        setTitle("Каркассон - Упрощенная версия");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1200, 800));
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        lobbyPanel = new LobbyPanel(controller);
        gamePanel = new GamePanel(controller);

        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(gamePanel, "GAME");

        add(mainPanel);
    }

    public void showLobby() {
        setVisible(true);
        cardLayout.show(mainPanel, "LOBBY");
    }

    public void showGame() {
        cardLayout.show(mainPanel, "GAME");
        gamePanel.initialize();
    }

    public GamePanel getGamePanel() {
        return gamePanel;
    }
}