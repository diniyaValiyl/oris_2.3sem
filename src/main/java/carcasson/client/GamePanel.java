package carcasson.client;

import carcasson.controller.AppController;
import carcasson.common.Tile;
import carcasson.common.Player;
import javax.swing.*;
import java.awt.*;

public class GamePanel extends JPanel {
    private AppController controller;
    private BoardPanel boardPanel;
    private TilePanel tilePanel;
    private ControlPanel controlPanel;
    private ChatPanel chatPanel;
    private PlayerPanel playerPanel;

    public GamePanel(AppController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(5, 5));
        setBackground(new Color(240, 240, 240));

        boardPanel = new BoardPanel(controller);
        tilePanel = new TilePanel(controller);
        controlPanel = new ControlPanel(controller);
        chatPanel = new ChatPanel(controller);
        playerPanel = new PlayerPanel(controller);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(350, 0));

        JPanel topRight = new JPanel(new GridLayout(2, 1, 0, 5));
        topRight.add(tilePanel);
        topRight.add(controlPanel);

        rightPanel.add(topRight, BorderLayout.NORTH);
        rightPanel.add(chatPanel, BorderLayout.CENTER);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(boardPanel, BorderLayout.CENTER);
        leftPanel.add(playerPanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    public void initialize() {
        boardPanel.initialize();
        playerPanel.setPlayerName(controller.getUsername());
        appendChatMessage("=== ПРАВИЛА ИГРЫ ===");
        appendChatMessage("• tile-l.png: РАЗВИЛКА ДОРОГ - нельзя ставить мипла");
        appendChatMessage("• tile-n.png: ГОРОД - при соединении 2 плиток = 4 очка");
        appendChatMessage("• tile-monastery.png: МОНАСТЫРЬ - 1+соседи очков");
        appendChatMessage("• tile-q.png, tile-r.png: ДОРОГИ - 1 очко за плитку");
        appendChatMessage("");
        appendChatMessage("Подключение установлено. Ожидаем второго игрока...");
    }

    public void gameStarted() {
        controlPanel.setEnabled(true);
        appendChatMessage("Второй игрок подключился! Игра начинается.");
        appendChatMessage("Колода создана случайным образом из 18 плиток.");
    }

    public void setCurrentTile(Tile tile) {
        tilePanel.setTile(tile);
    }

    public void setPlayerTurn(String playerName) {
        playerPanel.setCurrentTurn(playerName);
        boolean ourTurn = playerName.equals(controller.getUsername());
        tilePanel.setEnabled(ourTurn);
        controlPanel.setEnabled(ourTurn);
    }

    public void updateGameState(Object gameState) {
        System.out.println("GamePanel.updateGameState() вызван");
        boardPanel.updateBoard(gameState);
    }

    public void appendChatMessage(String message) {
        chatPanel.appendMessage(message);
    }

    public void gameEnded(String result) {
        appendChatMessage("=== ИГРА ОКОНЧЕНА ===");
        appendChatMessage(result);
        controlPanel.setEnabled(false);
        tilePanel.setEnabled(false);
    }

    public void setPlayerInfo(Player player) {
        playerPanel.setPlayerInfo(player);
    }

    public void updatePlayerScore(Player player) {
        playerPanel.updateScore(player.getScore(), player.getMeeplesLeft());
        appendChatMessage("Ваш счет: " + player.getScore() + " очков");
        appendChatMessage("Осталось миплов: " + player.getMeeplesLeft());
    }
}