package carcasson.controller;

import carcasson.common.GameMessage;
import java.io.*;
import java.net.*;

public class NetworkController {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private AppController appController;

    public NetworkController(AppController appController) {
        this.appController = appController;
    }

    public void connect(InetAddress host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            new Thread(this::receiveMessages).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveMessages() {
        try {
            while (!socket.isClosed()) {
                GameMessage message = (GameMessage) in.readObject();
                System.out.println("NetworkController получил сообщение типа: " + message.getType());
                appController.handleMessage(message);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Ошибка при получении сообщения: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void sendMessage(GameMessage message) {
        try {
            out.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}