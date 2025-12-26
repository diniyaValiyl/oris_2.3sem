package carcasson.server;

import carcasson.common.Tile;
import carcasson.common.Player;
import carcasson.common.GameMessage;
import java.util.*;

public class GameRoom {
    private List<ClientHandler> players = new ArrayList<>();
    private Map<String, Player> playerInfo = new HashMap<>();
    private List<Tile> deck = new ArrayList<>();
    private Tile[][] board = new Tile[15][15];
    private Map<String, Tile> playerTiles = new HashMap<>();
    private int currentPlayerIndex = 0;
    private boolean gameStarted = false;
    private boolean gameEnded = false;
    private Random random = new Random();

    // ДЛЯ УСТАНОВКИ МИПЛА: запоминаем последнюю плитку
    private String lastPlacedBy = null;
    private int lastPlacedX = -1;
    private int lastPlacedY = -1;

    // Карта занятости объектов
    private Map<String, String> occupiedObjects = new HashMap<>();

    public GameRoom() {
        createRandomDeck();
    }

    private void createRandomDeck() {
        deck.clear();

        String[][] allTiles = {
                {"tile-l.png", "N=N S=S W=S E=S NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"},
                {"tile-n.png", "N=C S=N W=N E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"},
                {"tile-q.png", "N=S S=S W=N E=N NS=1 NE=0 NW=0 WE=0 SE=0 SW=0"},
                {"tile-r.png", "N=N S=S W=S E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=1"},
                {"tile-r-mirror.png", "N=N S=S W=N E=S NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"},
                {"tile-monastery.png", "N=N S=N W=N E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=0 CL=1"}
        };

        for (int i = 0; i < 18; i++) {
            int index = random.nextInt(allTiles.length);
            Tile tile = new Tile(
                    i + 1,
                    "/tiles/" + allTiles[index][0],
                    allTiles[index][1]
            );
            deck.add(tile);
        }

        System.out.println("Создана колода из " + deck.size() + " плиток");
    }

    public synchronized boolean addPlayer(ClientHandler player, String username) {
        if (players.size() >= 2) {
            player.sendMessage("Комната заполнена. Максимум 2 игрока.");
            return false;
        }

        players.add(player);

        String color = players.size() == 1 ? "RED" : "BLUE";
        Player playerObj = new Player(player.getId(), username, color);
        playerInfo.put(username, playerObj);

        player.sendMessage(new GameMessage(
                "CONNECT",
                "SERVER",
                playerObj
        ));

        broadcastChat("Игрок " + username + " (" + color + ") присоединился");

        if (players.size() == 2) {
            startGame();
        }

        return true;
    }

    public void removePlayer(String username) {
        ClientHandler playerToRemove = null;
        for (ClientHandler player : players) {
            if (player.getUsername().equals(username)) {
                playerToRemove = player;
                break;
            }
        }

        if (playerToRemove != null) {
            players.remove(playerToRemove);
            playerInfo.remove(username);

            System.out.println("Игрок " + username + " удален из игры");
            broadcastChat("Игрок " + username + " покинул игру");

            if (gameStarted && !gameEnded) {
                endGame("Игра прервана. Игрок " + username + " покинул игру.");
            }
        }
    }

    public void startGame() {
        if (players.size() == 2 && !gameStarted) {
            gameStarted = true;

            Tile startTile = new Tile(0, "/tiles/tile-s.png",
                    "N=C S=N W=S E=S NS=0 NE=0 NW=0 WE=1 SE=0 SW=0");
            startTile.setX(7);
            startTile.setY(7);
            board[7][7] = startTile;

            broadcastChat("=== ИГРА НАЧАЛАСЬ! ===");
            broadcastChat("Первый ход у " + players.get(0).getUsername());

            // Отправляем начальное состояние игры ВСЕМ игрокам
            broadcastGameState();

            drawTileForPlayer(players.get(0));
        }
    }

    private void drawTileForPlayer(ClientHandler player) {
        if (!deck.isEmpty()) {
            Tile tile = deck.remove(0);
            playerTiles.put(player.getUsername(), tile);

            player.sendMessage(new GameMessage(
                    "TILE_DRAWN",
                    "SERVER",
                    tile
            ));

            setCurrentTurn(player.getUsername());
        } else {
            endGame("Колода пуста!");
        }
    }

    private void setCurrentTurn(String username) {
        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage(
                    "PLAYER_TURN",
                    "SERVER",
                    username
            ));
        }
    }

    // ============ 1. ИСПРАВЛЕННАЯ ПРОВЕРКА УСТАНОВКИ ПЛИТКИ ============
    private boolean isValidPlacement(Tile tile, int x, int y) {
        // 1. Проверка границ
        if (x < 0 || x >= 15 || y < 0 || y >= 15) return false;

        // 2. Клетка должна быть пустой
        if (board[x][y] != null) return false;

        boolean hasNeighbor = false;

        // 3. Проверяем всех соседей
        // Запад
        if (x > 0 && board[x-1][y] != null) {
            hasNeighbor = true;
            Tile west = board[x-1][y];
            if (!Tile.areSidesCompatible(west.getSide('E'), tile.getSide('W'))) {
                return false;
            }
        }

        // Восток
        if (x < 14 && board[x+1][y] != null) {
            hasNeighbor = true;
            Tile east = board[x+1][y];
            if (!Tile.areSidesCompatible(east.getSide('W'), tile.getSide('E'))) {
                return false;
            }
        }

        // Север
        if (y > 0 && board[x][y-1] != null) {
            hasNeighbor = true;
            Tile north = board[x][y-1];
            if (!Tile.areSidesCompatible(north.getSide('S'), tile.getSide('N'))) {
                return false;
            }
        }

        // Юг
        if (y < 14 && board[x][y+1] != null) {
            hasNeighbor = true;
            Tile south = board[x][y+1];
            if (!Tile.areSidesCompatible(south.getSide('N'), tile.getSide('S'))) {
                return false;
            }
        }

        // 4. Для первой плитки после стартовой - должен быть сосед
        return hasNeighbor || (board[7][7] != null && isAdjacentToStart(x, y));
    }

    private boolean isAdjacentToStart(int x, int y) {
        // Проверяем, соседствует ли с центральной плиткой (7,7)
        return (x == 7 && (y == 6 || y == 8)) ||
                (y == 7 && (x == 6 || x == 8));
    }

    // ============ 2. ИСПРАВЛЕННАЯ УСТАНОВКА ПЛИТКИ ============
    public void placeTile(String username, int[] coords) {
        Tile tile = playerTiles.get(username);
        if (tile == null) {
            System.out.println("Игрок " + username + " не имеет плитки для размещения");
            return;
        }

        int x = coords[0];
        int y = coords[1];

        System.out.println(username + " пытается разместить плитку " +
                tile.getFileName() + " в (" + x + "," + y + ")");

        if (isValidPlacement(tile, x, y)) {
            // Создаем новую плитку для доски
            Tile placedTile = new Tile(
                    tile.getId(),
                    tile.getImagePath(),
                    tile.getFeatures()
            );
            placedTile.setX(x);
            placedTile.setY(y);
            placedTile.setRotation(tile.getRotation());

            // ЗАПОМИНАЕМ ДЛЯ УСТАНОВКИ МИПЛА
            lastPlacedBy = username;
            lastPlacedX = x;
            lastPlacedY = y;

            board[x][y] = placedTile;
            playerTiles.remove(username);

            System.out.println("Плитка успешно размещена!");

            // Отправляем обновление ВСЕМ игрокам
            broadcastChat(username + " разместил плитку в (" + x + "," + y + ")");
            broadcastGameState();

            // ============ ОБНОВЛЕННОЕ СООБЩЕНИЕ ДЛЯ ИГРОКА ============
            // Сообщаем игроку о возможностях с учетом canPlaceMeeple()
            String tileType = placedTile.getType();
            String message = "Плитка размещена! Тип: " + tileType;

            if (placedTile.canPlaceMeeple()) {
                message += ". Вы можете поставить мипла (M) или пропустить ход (S)";
            } else {
                if (placedTile.getFileName() != null &&
                        (placedTile.getFileName().equals("tile-l.png") ||
                                placedTile.getFileName().equals("tile-s.png"))) {
                    message += ". На эту плитку нельзя ставить мипла. Пропустите ход (S)";
                } else {
                    message += ". Нельзя поставить мипла на эту плитку. Пропустите ход (S)";
                }
            }

            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    message
            ));
            // ============ КОНЕЦ ОБНОВЛЕННОГО СООБЩЕНИЯ ============

        } else {
            System.out.println("Некорректное размещение!");
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "Нельзя разместить плитку здесь. Проверьте совместимость с соседями."
            ));
        }
    }

    public void rotateTile(String username) {
        Tile tile = playerTiles.get(username);
        if (tile != null) {
            tile.rotate();

            // ВАЖНО: Отправляем обновленную плитку игроку
            getPlayer(username).sendMessage(new GameMessage(
                    "TILE_DRAWN",
                    "SERVER",
                    tile
            ));

            System.out.println(username + " повернул плитку " + tile.getFileName() +
                    " на " + tile.getRotation() * 90 + " градусов");
        }
    }

    // ============ 3. ИСПРАВЛЕННАЯ УСТАНОВКА МИПЛА ============
    public void placeMeeple(String username) {
        // Проверяем, что игрок размещает мипла на свою последнюю плитку
        if (!username.equals(lastPlacedBy)) {
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "Вы можете ставить мипла только на свою последнюю размещённую плитку!"
            ));
            return;
        }

        if (lastPlacedX == -1 || lastPlacedY == -1) {
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "Сначала разместите плитку!"
            ));
            return;
        }

        Tile tile = board[lastPlacedX][lastPlacedY];

        if (tile == null) {
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "Плитка не найдена!"
            ));
            return;
        }

        Player player = playerInfo.get(username);

        if (player.getMeeplesLeft() <= 0) {
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "У вас не осталось миплов!"
            ));
            return;
        }

        // ============ ВАЖНОЕ ИСПРАВЛЕНИЕ: проверка можно ли ставить мипла на эту плитку ============
        if (!tile.canPlaceMeeple()) {
            String fileName = tile.getFileName();
            String message;

            if (fileName != null && fileName.equals("tile-l.png")) {
                message = "На плитку tile-l.png нельзя ставить мипла!";
            } else if (fileName != null && fileName.equals("tile-s.png")) {
                message = "На начальную плитку tile-s.png нельзя ставить мипла!";
            } else if (tile.getType().equals("FIELD")) {
                message = "Нельзя поставить мипла на поле!";
            } else {
                message = "Нельзя поставить мипла на эту плитку!";
            }

            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    message
            ));
            return;
        }

        String tileType = tile.getType();

        if (tile.hasMeeple()) {
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "На этой плитке уже есть мипл!"
            ));
            return;
        }

        // ============ ПРОВЕРКА ЗАНЯТОСТИ ОБЪЕКТА ============
        // Находим все плитки объекта
        List<Tile> objectTiles = findConnectedTiles(lastPlacedX, lastPlacedY, tileType);

        // Проверяем, не занят ли весь объект другим игроком
        if (isWholeObjectOccupied(objectTiles, player.getColor())) {
            getPlayer(username).sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    "Этот " + tileType.toLowerCase() + " уже занят другим игроком!"
            ));
            return;
        }

        // Также проверяем, нет ли уже мипла на любой из плиток объекта
        for (Tile t : objectTiles) {
            if (t.hasMeeple()) {
                getPlayer(username).sendMessage(new GameMessage(
                        "CHAT_MESSAGE",
                        "SERVER",
                        "На этом " + tileType.toLowerCase() + " уже есть мипл!"
                ));
                return;
            }
        }

        // Устанавливаем мипла на все плитки объекта
        for (Tile t : objectTiles) {
            t.setMeeple(true, player.getColor(), tileType);
            t.occupyFeature(tileType, player.getColor());

            // Занимаем объект
            String objectKey = generateObjectKey(t.getX(), t.getY(), tileType);
            occupiedObjects.put(objectKey, player.getColor());
        }

        player.useMeeple();

        // ============ СИСТЕМА ПОДСЧЕТА ОЧКОВ ============
        int points = calculatePoints(objectTiles, tileType, lastPlacedX, lastPlacedY);
        player.addScore(points);

        System.out.println(username + " поставил мипла на " + tileType +
                " в (" + lastPlacedX + "," + lastPlacedY + ") размер: " +
                objectTiles.size() + " плиток, +" + points + " очков");

        broadcastChat(username + " поставил мипла на " + tileType.toLowerCase() +
                " (" + objectTiles.size() + " плиток, +" + points + " очков)");
        broadcastScoreUpdate(username);
        broadcastGameState();

        // Сбрасываем информацию о последней плитке
        lastPlacedBy = null;
        lastPlacedX = -1;
        lastPlacedY = -1;

        nextTurn();
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ПРОВЕРКИ ЗАНЯТОСТИ ============

    private String generateObjectKey(int x, int y, String type) {
        // Генерируем уникальный ключ для объекта
        return x + "," + y + "," + type;
    }

    private List<Tile> findConnectedTiles(int startX, int startY, String featureType) {
        List<Tile> connected = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<int[]> queue = new LinkedList<>();

        queue.add(new int[]{startX, startY});
        visited.add(startX + "," + startY);

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            Tile tile = board[x][y];

            if (tile != null) {
                connected.add(tile);

                // Проверяем всех соседей
                int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

                for (int[] dir : directions) {
                    int nx = x + dir[0];
                    int ny = y + dir[1];

                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                        String key = nx + "," + ny;
                        if (!visited.contains(key) && board[nx][ny] != null) {
                            // Проверяем, соединены ли объекты
                            if (areFeaturesConnected(tile, board[nx][ny], dir[0], dir[1], featureType)) {
                                queue.add(new int[]{nx, ny});
                                visited.add(key);
                            }
                        }
                    }
                }
            }
        }

        return connected;
    }

    private boolean areFeaturesConnected(Tile tile1, Tile tile2, int dx, int dy, String featureType) {
        // Упрощенная проверка: объекты соединены, если стороны совместимы
        char side1, side2;

        if (dx == -1) { // tile1 западнее tile2
            side1 = tile1.getSide('E');
            side2 = tile2.getSide('W');
        } else if (dx == 1) { // tile1 восточнее tile2
            side1 = tile1.getSide('W');
            side2 = tile2.getSide('E');
        } else if (dy == -1) { // tile1 севернее tile2
            side1 = tile1.getSide('S');
            side2 = tile2.getSide('N');
        } else { // tile1 южнее tile2
            side1 = tile1.getSide('N');
            side2 = tile2.getSide('S');
        }

        // Для дороги: обе стороны должны быть 'S'
        if (featureType.equals("ROAD")) {
            return side1 == 'S' && side2 == 'S';
        }
        // Для города: обе стороны должны быть 'C'
        else if (featureType.equals("CITY")) {
            return side1 == 'C' && side2 == 'C';
        }
        // Для монастыря: всегда только одна плитка
        else if (featureType.equals("MONASTERY")) {
            return false; // Монастырь не соединяется
        }

        return false;
    }

    private boolean isWholeObjectOccupied(List<Tile> objectTiles, String currentPlayerColor) {
        // Проверяем, занят ли хотя бы одна плитка объекта другим игроком
        for (Tile tile : objectTiles) {
            if (tile.hasMeeple() && !tile.getMeepleOwner().equals(currentPlayerColor)) {
                return true;
            }
        }
        return false;
    }

    // ============ 5. ИСПРАВЛЕННЫЙ ПОДСЧЕТ ОЧКОВ ============
    private int calculatePoints(List<Tile> objectTiles, String tileType, int x, int y) {
        switch (tileType) {
            case "CITY":
                // Для города: 2 очка за каждую плитку города
                return objectTiles.size() * 2;

            case "ROAD":
                // Для дороги: 1 очко за каждую плитку дороги
                return objectTiles.size();

            case "MONASTERY":
                // Для монастыря: 1 очко + по 1 за каждого соседа (макс 9)
                return 1 + countNeighbors(x, y);

            default:
                return 0;
        }
    }

    private int countNeighbors(int x, int y) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && board[nx][ny] != null) {
                    count++;
                }
            }
        }
        return count;
    }

    // ============ 6. ИСПРАВЛЕННЫЙ ПРОПУСК ХОДА ============
    public void skipTurn(String username) {
        // Если игрок пропускает ход после размещения плитки
        if (username.equals(lastPlacedBy)) {
            lastPlacedBy = null;
            lastPlacedX = -1;
            lastPlacedY = -1;
        }

        broadcastChat(username + " пропустил ход");
        nextTurn();
    }

    private void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        if (!deck.isEmpty()) {
            drawTileForPlayer(players.get(currentPlayerIndex));
        } else {
            endGame("Колода пуста!");
        }
    }

    private void broadcastGameState() {
        System.out.println("=== Отправка состояния игры ===");
        int tileCount = 0;

        // Создаем копию массива для отправки (чтобы избежать проблем с сериализацией)
        Tile[][] boardCopy = new Tile[15][15];

        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[i].length; j++) {
                Tile original = board[i][j];
                if (original != null) {
                    tileCount++;

                    // Создаем новую плитку для отправки
                    Tile copy = new Tile(
                            original.getId(),
                            original.getImagePath(),
                            original.getFeatures()
                    );
                    copy.setX(original.getX());
                    copy.setY(original.getY());
                    copy.setRotation(original.getRotation());

                    if (original.hasMeeple()) {
                        copy.setMeeple(true, original.getMeepleOwner(), original.getMeepleType());
                    }

                    boardCopy[i][j] = copy;

                    System.out.println("  [" + i + "," + j + "] -> " +
                            original.getFileName() +
                            " (x=" + original.getX() + ", y=" + original.getY() +
                            ", rot=" + original.getRotation() + ")");
                }
            }
        }

        System.out.println("Всего плиток на сервере: " + tileCount);
        System.out.println("Отправляем копию массива размера " + boardCopy.length + "x" + boardCopy[0].length);

        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage(
                    "GAME_STATE",
                    "SERVER",
                    boardCopy
            ));
        }
    }

    private void broadcastScoreUpdate(String username) {
        Player player = playerInfo.get(username);
        if (player != null) {
            for (ClientHandler p : players) {
                p.sendMessage(new GameMessage(
                        "SCORE_UPDATE",
                        "SERVER",
                        player
                ));
            }
        }
    }

    public void broadcastChat(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage(
                    "CHAT_MESSAGE",
                    "SERVER",
                    message
            ));
        }
    }

    private ClientHandler getPlayer(String username) {
        for (ClientHandler player : players) {
            if (player.getUsername().equals(username)) {
                return player;
            }
        }
        return null;
    }

    public void endGame(String reason) {
        if (gameEnded) return;

        gameEnded = true;

        String winnerMessage = determineWinner();
        String finalMessage = "=== ИГРА ОКОНЧЕНА ===\n" + reason + "\n\n" + winnerMessage;

        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage(
                    "GAME_END",
                    "SERVER",
                    finalMessage
            ));
        }

        System.out.println("Игра окончена: " + reason);
    }

    private String determineWinner() {
        List<Player> playerList = new ArrayList<>(playerInfo.values());

        if (playerList.size() == 1) {
            return "Победитель: " + playerList.get(0).getName();
        }

        Player player1 = playerList.get(0);
        Player player2 = playerList.get(1);

        if (player1.getScore() > player2.getScore()) {
            return "ПОБЕДИТЕЛЬ: " + player1.getName() + " (" + player1.getScore() + " очков)";
        } else if (player2.getScore() > player1.getScore()) {
            return "ПОБЕДИТЕЛЬ: " + player2.getName() + " (" + player2.getScore() + " очков)";
        } else {
            return "НИЧЬЯ! Оба игрока набрали " + player1.getScore() + " очков";
        }
    }

    public int getPlayerCount() {
        return players.size();
    }
}