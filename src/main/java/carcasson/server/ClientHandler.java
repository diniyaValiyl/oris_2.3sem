package carcasson.server;

import carcasson.common.GameMessage;
import java.io.*;
import java.net.*;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private Socket socket;
    private GameServer server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String username;
    private String id;

    public ClientHandler(Socket socket, GameServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        this.id = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        try {
            GameMessage connectMessage = (GameMessage) in.readObject();
            if (connectMessage.getType().equals("CONNECT")) {
                this.username = (String) connectMessage.getSender();

                System.out.println("Игрок подключился: " + username + " (ID: " + id + ")");

                boolean success = server.getGameRoom().addPlayer(this, username);

                if (success) {
                    sendMessage(new GameMessage(
                            "CONNECT",
                            "SERVER",
                            "Добро пожаловать, " + username + "! Ожидаем второго игрока..."
                    ));

                    if (server.getGameRoom().getPlayerCount() == 2) {
                        server.getGameRoom().startGame();
                    }

                    while (!socket.isClosed()) {
                        GameMessage message = (GameMessage) in.readObject();
                        handleMessage(message);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Ошибка соединения с " + username + ": " + e.getMessage());
        } finally {
            server.removeClient(this);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(GameMessage message) {
        String type = message.getType();

        switch (type) {
            case "TILE_PLACED":
                server.getGameRoom().placeTile(username, (int[]) message.getData());
                break;
            case "TILE_ROTATED":
                server.getGameRoom().rotateTile(username);
                break;
            case "MEEPLE_PLACED":
                // Упрощенная версия - без параметров
                server.getGameRoom().placeMeeple(username);
                break;
            case "TURN_SKIP":
                server.getGameRoom().skipTurn(username);
                break;
            case "CHAT_MESSAGE":
                server.getGameRoom().broadcastChat(username + ": " + message.getData());
                break;
            case "END_GAME":
                server.getGameRoom().endGame("Игра завершена по запросу игрока");
                break;
            default:
                System.out.println("Неизвестный тип сообщения от " + username + ": " + type);
        }
    }

    public void sendMessage(GameMessage message) {
        try {
            out.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String text) {
        sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", text));
    }

    public String getUsername() {
        return username;
    }

    public String getId() {
        return id;
    }
}