package carcasson.controller;

import carcasson.client.GameFrame;
import carcasson.common.GameMessage;
import carcasson.common.Tile;
import carcasson.common.Player;
import javax.swing.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class AppController {
    private NetworkController networkController;
    private GameFrame gameFrame;
    private String username;

    public AppController() {
        SwingUtilities.invokeLater(() -> {
            this.gameFrame = new GameFrame(this);
            this.gameFrame.showLobby();
            initControllers();
        });
    }

    private void initControllers() {
        this.networkController = new NetworkController(this);
    }

    public void connectToServer(String username, String serverAddress) {
        this.username = username;
        try {
            networkController.connect(InetAddress.getByName(serverAddress), 8888);
            networkController.sendMessage(new GameMessage(
                    "CONNECT",
                    username,
                    null
            ));
        } catch (UnknownHostException e) {
            showError("Неверный адрес сервера: " + e.getMessage());
        }
    }
    public void handleMessage(GameMessage message) {
        SwingUtilities.invokeLater(() -> {
            String type = message.getType();
            System.out.println("AppController.handleMessage() - тип: " + type + ", данные: " + message.getData());

            switch (type) {
                case "CONNECT":
                    Object data = message.getData();
                    if (data instanceof String) {
                        gameFrame.getGamePanel().appendChatMessage((String) data);
                    } else if (data instanceof Player) {
                        Player player = (Player) data;
                        gameFrame.getGamePanel().setPlayerInfo(player);
                    }
                    gameFrame.showGame();
                    break;

                case "GAME_START":
                    gameFrame.getGamePanel().gameStarted();
                    gameFrame.getGamePanel().appendChatMessage("=== ИГРА НАЧАЛАСЬ ===");
                    break;

                case "TILE_DRAWN":
                    if (message.getData() instanceof Tile) {
                        Tile tile = (Tile) message.getData();
                        gameFrame.getGamePanel().setCurrentTile(tile);
                        gameFrame.getGamePanel().appendChatMessage("Вы получили плитку: " + tile.getFileName());
                    }
                    break;

                case "PLAYER_TURN":
                    String currentPlayer = (String) message.getData();
                    gameFrame.getGamePanel().setPlayerTurn(currentPlayer);

                    if (currentPlayer.equals(username)) {
                        gameFrame.getGamePanel().appendChatMessage("=== ВАШ ХОД ===");
                    } else {
                        gameFrame.getGamePanel().appendChatMessage("Ход игрока: " + currentPlayer);
                    }
                    break;

                case "GAME_STATE":
                    System.out.println("AppController: получено GAME_STATE");
                    System.out.println("Данные типа: " + (message.getData() != null ? message.getData().getClass().getName() : "null"));

                    // Проверяем, что это действительно массив плиток
                    if (message.getData() instanceof Tile[][]) {
                        Tile[][] board = (Tile[][]) message.getData();
                        System.out.println("Размер массива: " + board.length + "x" +
                                (board.length > 0 ? board[0].length : 0));

                        // Считаем ненулевые плитки
                        int tileCount = 0;
                        for (int i = 0; i < board.length; i++) {
                            for (int j = 0; j < board[i].length; j++) {
                                if (board[i][j] != null) {
                                    tileCount++;
                                }
                            }
                        }
                        System.out.println("Плиток в массиве: " + tileCount);
                    }

                    // Обновляем игровое поле с новым состоянием
                    gameFrame.getGamePanel().updateGameState(message.getData());
                    break;

                case "CHAT_MESSAGE":
                    gameFrame.getGamePanel().appendChatMessage((String) message.getData());
                    break;

                case "SCORE_UPDATE":
                    if (message.getData() instanceof Player) {
                        Player player = (Player) message.getData();
                        if (player.getName().equals(username)) {
                            gameFrame.getGamePanel().updatePlayerScore(player);
                        }
                    }
                    break;

                case "GAME_END":
                    String result = (String) message.getData();
                    gameFrame.getGamePanel().gameEnded(result);

                    JOptionPane.showMessageDialog(
                            gameFrame,
                            result,
                            "Игра окончена",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    break;

                default:
                    System.out.println("Неизвестный тип сообщения: " + type);
            }
        });
    }

    public void sendChatMessage(String text) {
        networkController.sendMessage(new GameMessage(
                "CHAT_MESSAGE",
                username,
                text
        ));
    }

    public void sendTilePlaced(int x, int y) {
        networkController.sendMessage(new GameMessage(
                "TILE_PLACED",
                username,
                new int[]{x, y}
        ));
    }

    public void sendTileRotated() {
        networkController.sendMessage(new GameMessage(
                "TILE_ROTATED",
                username,
                null
        ));
    }

    public void sendMeeplePlaced() {
        // Упрощенная версия - просто ставим мипла без указания типа и позиции
        networkController.sendMessage(new GameMessage(
                "MEEPLE_PLACED",
                username,
                null
        ));
    }

    public void sendTurnSkip() {
        networkController.sendMessage(new GameMessage(
                "TURN_SKIP",
                username,
                null
        ));
    }

    public void sendEndGame() {
        networkController.sendMessage(new GameMessage(
                "END_GAME",
                username,
                null
        ));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    public String getUsername() {
        return username;
    }
}