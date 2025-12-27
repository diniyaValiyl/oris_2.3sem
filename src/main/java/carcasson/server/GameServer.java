package carcasson.server;

import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private GameRoom gameRoom;

    public GameServer() {
        this.gameRoom = new GameRoom();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(8888);
            System.out.println("Сервер запущен на порту 8888");
            System.out.println("IP адрес: " + InetAddress.getLocalHost().getHostAddress());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void broadcastToAll(String message, ClientHandler exclude) {
        for (ClientHandler client : clients) {
            if (client != exclude) {
                client.sendMessage(message);
            }
        }
    }

    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        String username = client.getUsername();
        System.out.println("Клиент отключен" + (username != null ? ": " + username : " (не авторизован)"));

        // Если игра идет и отключился игрок, завершаем игру
        if (username != null && gameRoom.getPlayerCount() >= 2) {
            gameRoom.endGame("Игра завершена: игрок " + username + " отключился");
        }
    }

    public GameRoom getGameRoom() {
        return gameRoom;
    }
}