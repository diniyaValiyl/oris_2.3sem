package carcasson.server;

public class ServerMain {
    public static void main(String[] args) {
        System.out.println("=== ЗАПУСК СЕРВЕРА КАРКАССОН ===");
        System.out.println("Порт: 8888");
        System.out.println("Ожидание подключения 2 игроков...");

        GameServer server = new GameServer();
        server.start();
    }
}