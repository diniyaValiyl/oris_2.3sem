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

    private String lastPlacedBy = null;
    private int lastPlacedX = -1;
    private int lastPlacedY = -1;

    private Map<String, String> objectOwners = new HashMap<>();
    private Map<String, Boolean> completedCities = new HashMap<>();

    public GameRoom() {
        createRandomDeck();
    }

    private void createRandomDeck() {
        deck.clear();
        List<String[]> allTiles = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            allTiles.add(new String[]{"tile-n.png", "N=C S=N W=N E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"});
        }

        for (int i = 0; i < 3; i++) {
            allTiles.add(new String[]{"tile-monastery.png", "N=N S=N W=N E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=0 CL=1"});
        }


        for (int i = 0; i < 2; i++) {
            allTiles.add(new String[]{"tile-l.png", "N=N S=S W=S E=S NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"});
        }


        String[][] roadTiles = {
                {"tile-q.png", "N=S S=S W=N E=N NS=1 NE=0 NW=0 WE=0 SE=0 SW=0"},
                {"tile-r.png", "N=N S=S W=S E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=1"},
                {"tile-r-mirror.png", "N=N S=S W=N E=S NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"}
        };

        for (int i = 0; i < 9; i++) {
            int index = random.nextInt(roadTiles.length);
            allTiles.add(roadTiles[index]);
        }

        Collections.shuffle(allTiles, random);

        for (int i = 0; i < allTiles.size(); i++) {
            String[] tileData = allTiles.get(i);
            Tile tile = new Tile(
                    i + 1,
                    "/tiles/" + tileData[0],
                    tileData[1]
            );
            deck.add(tile);
        }

        Map<String, Integer> tileCount = new HashMap<>();
        for (Tile tile : deck) {
            String fileName = tile.getFileName();
            tileCount.put(fileName, tileCount.getOrDefault(fileName, 0) + 1);
        }

        System.out.println("СОСТАВ КОЛОДЫ");
        for (Map.Entry<String, Integer> entry : tileCount.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue() + " шт.");
        }
        System.out.println("Всего плиток: " + deck.size());
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

        player.sendMessage(new GameMessage("CONNECT", "SERVER", playerObj));
        broadcastChat("Игрок " + username + " (" + color + ") присоединился");

        if (players.size() == 2) {
            startGame();
        }

        return true;
    }

    public void startGame() {
        if (players.size() == 2 && !gameStarted) {
            gameStarted = true;

            Tile startTile = new Tile(0, "/tiles/tile-q.png",
                    "N=S S=S W=N E=N NS=1 NE=0 NW=0 WE=0 SE=0 SW=0");
            startTile.setX(7);
            startTile.setY(7);
            board[7][7] = startTile;

            broadcastChat("=== ИГРА НАЧАЛАСЬ! ===");
            broadcastChat("Первый ход у " + players.get(0).getUsername());

            broadcastGameState();
            drawTileForPlayer(players.get(0));
        }
    }

    private void drawTileForPlayer(ClientHandler player) {
        if (!deck.isEmpty()) {
            Tile tile = deck.remove(0);
            playerTiles.put(player.getUsername(), tile);

            player.sendMessage(new GameMessage("TILE_DRAWN", "SERVER", tile));
            setCurrentTurn(player.getUsername());
        } else {
            endGame("Колода пуста!");
        }
    }

    private void setCurrentTurn(String username) {
        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage("PLAYER_TURN", "SERVER", username));
        }
    }

    private boolean isValidPlacement(Tile tile, int x, int y) {
        if (x < 0 || x >= 15 || y < 0 || y >= 15) return false;
        if (board[x][y] != null) return false;

        boolean hasNeighbor = false;
        boolean allCompatible = true;

        if (x > 0 && board[x-1][y] != null) {
            hasNeighbor = true;
            Tile west = board[x-1][y];
            if (!areSidesCompatible(west.getSide('E'), tile.getSide('W'), west, tile)) {
                allCompatible = false;
            }
        }

        if (x < 14 && board[x+1][y] != null) {
            hasNeighbor = true;
            Tile east = board[x+1][y];
            if (!areSidesCompatible(east.getSide('W'), tile.getSide('E'), east, tile)) {
                allCompatible = false;
            }
        }

        if (y > 0 && board[x][y-1] != null) {
            hasNeighbor = true;
            Tile north = board[x][y-1];
            if (!areSidesCompatible(north.getSide('S'), tile.getSide('N'), north, tile)) {
                allCompatible = false;
            }
        }

        if (y < 14 && board[x][y+1] != null) {
            hasNeighbor = true;
            Tile south = board[x][y+1];
            if (!areSidesCompatible(south.getSide('N'), tile.getSide('S'), south, tile)) {
                allCompatible = false;
            }
        }

        return hasNeighbor && allCompatible && (board[7][7] != null || isAdjacentToStart(x, y));
    }

    private boolean areSidesCompatible(char side1, char side2, Tile tile1, Tile tile2) {
        if ("tile-n.png".equals(tile1.getFileName()) && side1 == 'C') {

            return side2 == 'C' && "tile-n.png".equals(tile2.getFileName());
        }

        if ("tile-n.png".equals(tile2.getFileName()) && side2 == 'C') {
            return side1 == 'C' && "tile-n.png".equals(tile1.getFileName());
        }

        if (side1 == 'S' && side2 == 'S') {
            return true;
        }

        if (side1 == 'N' && side2 == 'N') {
            return true;
        }

        if ("tile-l.png".equals(tile1.getFileName()) && side1 == 'S') {
            return side2 == 'S';
        }

        if ("tile-l.png".equals(tile2.getFileName()) && side2 == 'S') {
            return side1 == 'S';
        }

        return false;
    }

    private boolean isAdjacentToStart(int x, int y) {
        return (x == 7 && (y == 6 || y == 8)) || (y == 7 && (x == 6 || x == 8));
    }

    public void placeTile(String username, int[] coords) {
        Tile tile = playerTiles.get(username);
        if (tile == null) {
            System.out.println("Игрок " + username + " не имеет плитки для размещения");
            return;
        }

        int x = coords[0];
        int y = coords[1];



        if (isValidPlacement(tile, x, y)) {
            Tile placedTile = new Tile(tile.getId(), tile.getImagePath(), tile.getFeatures());
            placedTile.setX(x);
            placedTile.setY(y);
            placedTile.setRotation(tile.getRotation());

            lastPlacedBy = username;
            lastPlacedX = x;
            lastPlacedY = y;

            board[x][y] = placedTile;
            playerTiles.remove(username);

            if ("tile-n.png".equals(placedTile.getFileName())) {
                checkCityCompletionForTileN(x, y);
            }

            System.out.println("Плитка успешно размещена!");
            broadcastChat(username + " разместил плитку в (" + x + "," + y + ")");
            broadcastGameState();


            String message = getPlacementOptionsMessage(placedTile, username);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
        } else {
            System.out.println("Некорректное размещение!");
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER",
                    "Нельзя разместить плитку здесь. Проверьте совместимость с соседями."));
        }
    }


    private char getDirectionChar(int[] dir) {
        if (dir[0] == -1) return 'W';
        if (dir[0] == 1) return 'E';
        if (dir[1] == -1) return 'N';
        if (dir[1] == 1) return 'S';
        return 'N';
    }

    private char getOppositeDirectionChar(int[] dir) {
        if (dir[0] == -1) return 'E';
        if (dir[0] == 1) return 'W';
        if (dir[1] == -1) return 'S';
        if (dir[1] == 1) return 'N';
        return 'S';
    }

    private char getOppositeSide(char side) {
        switch (side) {
            case 'N': return 'S';
            case 'S': return 'N';
            case 'E': return 'W';
            case 'W': return 'E';
            default: return 'N';
        }
    }


    private void checkCityCompletionForTileN(int x, int y) {
        Tile placedTile = board[x][y];
        if (placedTile == null || !"tile-n.png".equals(placedTile.getFileName())) {
            return;
        }

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                Tile neighbor = board[nx][ny];
                if (neighbor != null && "tile-n.png".equals(neighbor.getFileName())) {
                    char side1 = placedTile.getSide(getDirectionChar(dir));
                    char side2 = neighbor.getSide(getOppositeDirectionChar(dir));

                    if (side1 == 'C' && side2 == 'C') {
                        completeCity(x, y, nx, ny);
                        return;
                    }
                }
            }
        }
    }
    private void completeCity(int x1, int y1, int x2, int y2) {
        String cityKey = x1 + "," + y1 + "-" + x2 + "," + y2;
        String reverseKey = x2 + "," + y2 + "-" + x1 + "," + y1;

        System.out.println("Координаты: (" + x1 + "," + y1 + ") и (" + x2 + "," + y2 + ")");
        System.out.println("Ключ: " + cityKey);
        System.out.println("Уже завершен? " + completedCities.containsKey(cityKey) +
                " или " + completedCities.containsKey(reverseKey));

        if (completedCities.containsKey(cityKey) || completedCities.containsKey(reverseKey)) {
            System.out.println("Город уже завершен - пропускаем");
            return;
        }

        completedCities.put(cityKey, true);
        String cityOwner = findCityOwner(x1, y1, x2, y2);

        System.out.println("Владелец города: " + cityOwner);

        if ("DIVIDED".equals(cityOwner)) {
            System.out.println("Город разделен между игроками");
            handleDividedCity(x1, y1, x2, y2);
            return;
        }

        if (cityOwner == null) {
            System.out.println("Город завершен, но никто не владеет им!");
            broadcastChat("Город завершен, но никто не владеет им!");
            return;
        }

        Player player = playerInfo.get(cityOwner);
        if (player == null) {
            System.out.println("Игрок не найден: " + cityOwner);
            return;
        }

        int points = 4;
        player.addScore(points);
        returnMeeplesForCity(x1, y1, x2, y2, cityOwner);

        System.out.println("Начислено " + points + " очков игроку " + cityOwner);
        broadcastChat("Владелец: " + cityOwner + " получает +" + points + " очков");
        broadcastScoreUpdate(cityOwner);
        broadcastGameState();
    }

    private String findCityOwner(int x1, int y1, int x2, int y2) {
        Tile tile1 = board[x1][y1];
        Tile tile2 = board[x2][y2];

        if (tile1 != null && tile1.hasMeeple() &&
                tile2 != null && tile2.hasMeeple()) {

            if (tile1.getMeepleOwner().equals(tile2.getMeepleOwner())) {
                return getPlayerNameByColor(tile1.getMeepleOwner());
            } else {
                return "DIVIDED";
            }
        }

        if (tile1 != null && tile1.hasMeeple()) {
            return getPlayerNameByColor(tile1.getMeepleOwner());
        }

        if (tile2 != null && tile2.hasMeeple()) {
            return getPlayerNameByColor(tile2.getMeepleOwner());
        }

        return null;
    }

    private void handleDividedCity(int x1, int y1, int x2, int y2) {
        Tile tile1 = board[x1][y1];
        Tile tile2 = board[x2][y2];

        if (tile1 == null || tile2 == null) return;

        String owner1 = tile1.getMeepleOwner();
        String owner2 = tile2.getMeepleOwner();

        if (owner1 == null || owner2 == null) return;

        Player player1 = getPlayerByColor(owner1);
        Player player2 = getPlayerByColor(owner2);

        if (player1 == null || player2 == null) return;

        int points = 4;
        int pointsEach = points / 2;

        player1.addScore(pointsEach);
        player2.addScore(pointsEach);

        returnMeeplesForCity(x1, y1, x2, y2, player1.getName());
        returnMeeplesForCity(x1, y1, x2, y2, player2.getName());


        broadcastChat("Каждый получает: +" + pointsEach + " очков");

        broadcastScoreUpdate(player1.getName());
        broadcastScoreUpdate(player2.getName());
        broadcastGameState();
    }

    private void returnMeeplesForCity(int x1, int y1, int x2, int y2, String username) {
        Player player = playerInfo.get(username);
        if (player == null) return;

        String playerColor = player.getColor();
        Tile tile1 = board[x1][y1];
        Tile tile2 = board[x2][y2];

        if (tile1 != null && tile1.hasMeeple() && tile1.getMeepleOwner().equals(playerColor)) {
            tile1.setMeeple(false, null, null);
            player.returnMeeple();
        }

        if (tile2 != null && tile2.hasMeeple() && tile2.getMeepleOwner().equals(playerColor)) {
            tile2.setMeeple(false, null, null);
            player.returnMeeple();
        }
    }

    public void rotateTile(String username) {
        Tile tile = playerTiles.get(username);
        if (tile != null) {
            tile.rotate();
            getPlayer(username).sendMessage(new GameMessage("TILE_DRAWN", "SERVER", tile));
            System.out.println(username + " повернул плитку " + tile.getFileName());
        }
    }

    public void placeMeeple(String username) {

        System.out.println("Игрок: " + username);
        System.out.println("lastPlacedBy: " + lastPlacedBy);
        System.out.println("lastPlacedX,Y: " + lastPlacedX + "," + lastPlacedY);

        // 1. Проверяем, что игрок ставит мипл на свою последнюю плитку
        if (!username.equals(lastPlacedBy)) {
            String message = "Вы можете ставить мипла только на свою последнюю размещённую плитку!";
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        if (lastPlacedX == -1 || lastPlacedY == -1) {
            String message = "Сначала разместите плитку!";
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 2. Получаем плитку и игрока
        Tile tile = board[lastPlacedX][lastPlacedY];
        Player player = playerInfo.get(username);


        // 3. Проверка на развилку tile-l.png
        if ("tile-l.png".equals(tile.getFileName())) {
            String message = "На плитку tile-l.png нельзя ставить мипла! Это развилка дорог.";
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 4. Проверка возможности размещения мипла
        if (!tile.canPlaceMeeple()) {
            String message = "Нельзя поставить мипла на эту плитку!";
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 5. Проверка наличия мипла на плитке
        if (tile.hasMeeple()) {
            String message = "На этой плитке уже есть мипл!";
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 6. Проверка доступности миплов у игрока
        if (player.getMeeplesLeft() <= 0) {
            String message = "У вас не осталось миплов!";
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 7. Определяем реальный тип плитки
        String tileType;
        String fileName = tile.getFileName();

        if ("tile-n.png".equals(fileName)) {
            tileType = "CITY";
            System.out.println("tile-n.png detected -> CITY type");
        } else if ("tile-monastery.png".equals(fileName)) {
            tileType = "MONASTERY";
            System.out.println("tile-monastery.png detected -> MONASTERY type");
        } else if ("tile-l.png".equals(fileName)) {
            tileType = "FORK";
            System.out.println("tile-l.png detected -> FORK type");
        } else {
            tileType = tile.getType();
            System.out.println("Regular tile -> type: " + tileType);
        }

        // 8. Обработка РАЗНЫХ типов плиток
        if (tileType.equals("ROAD")) {
            System.out.println("=== ОБРАБОТКА ДОРОГИ ===");

            List<Tile> currentSegment = findCompleteRoadSegment(tile.getX(), tile.getY());
            System.out.println("Текущий сегмент: " + currentSegment.size() + " плиток");

            for (Tile roadTile : currentSegment) {
                System.out.println("  (" + roadTile.getX() + "," + roadTile.getY() +
                        ") - " + roadTile.getFileName() +
                        ", мипл: " + roadTile.hasMeeple() +
                        ", владелец: " + (roadTile.hasMeeple() ? roadTile.getMeepleOwner() : "нет"));
            }

            boolean roadOccupiedByOther = false;
            String otherPlayerName = null;

            for (Tile roadTile : currentSegment) {
                if ("tile-l.png".equals(roadTile.getFileName())) {
                    continue;
                }

                if (roadTile.hasMeeple() && !roadTile.getMeepleOwner().equals(player.getColor())) {
                    roadOccupiedByOther = true;
                    otherPlayerName = getPlayerNameByColor(roadTile.getMeepleOwner());
                    System.out.println("Обнаружен мипл другого игрока в сегменте!");
                    break;
                }
            }

            if (roadOccupiedByOther) {
                String errorMessage = "Невозможно поставить мипл - эта часть дороги уже занята игроком " + otherPlayerName + "!";
                System.out.println("ОШИБКА: " + errorMessage);

                broadcastChat(username + " пытался поставить мипл на занятую часть дороги");
                getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", "❌ " + errorMessage));
                return;
            }

            boolean roadAlreadyHasOwnMeeple = false;
            for (Tile roadTile : currentSegment) {
                if ("tile-l.png".equals(roadTile.getFileName())) {
                    continue;
                }

                if (roadTile.hasMeeple() && roadTile.getMeepleOwner().equals(player.getColor())) {
                    roadAlreadyHasOwnMeeple = true;
                    System.out.println("Игрок уже имеет мипл в этой части дороги!");
                    break;
                }
            }

            if (roadAlreadyHasOwnMeeple) {
                String message = "Вы уже поставили мипла на эту часть дороги!";
                System.out.println(message);
                getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                return;
            }

            tile.setMeeple(true, player.getColor(), "ROAD");
            String key = tile.getX() + "," + tile.getY() + ":ROAD";
            objectOwners.put(key, player.getColor());
            player.useMeeple();

            // Подсчитываем статистику сегмента для сообщения
            int roadTilesInSegment = 0;
            int forksInSegment = 0;

            for (Tile roadTile : currentSegment) {
                if ("tile-l.png".equals(roadTile.getFileName())) {
                    forksInSegment++;
                } else {
                    roadTilesInSegment++;
                }
            }

            System.out.println("Мипл успешно поставлен на дорогу");
            String broadcastMsg = username + " поставил мипла на дорогу (" + roadTilesInSegment + " плиток дороги";
            if (forksInSegment > 0) {
                broadcastMsg += ", через " + forksInSegment + " развилок";
            }
            broadcastMsg += ")";
            broadcastChat(broadcastMsg);

        } else if (tileType.equals("MONASTERY")) {
            System.out.println(" ОБРАБОТКА МОНАСТЫРЯ ");

            String objectKey = tile.getX() + "," + tile.getY() + ":MONASTERY";
            String existingOwner = objectOwners.get(objectKey);

            if (existingOwner != null && !existingOwner.equals(player.getColor())) {
                String otherPlayerName = getPlayerNameByColor(existingOwner);
                String message = "Монастырь уже занят игроком " + otherPlayerName + "!";
                System.out.println(message);
                getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                return;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    int nx = tile.getX() + dx;
                    int ny = tile.getY() + dy;

                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                        Tile neighbor = board[nx][ny];
                        if (neighbor != null && neighbor.hasMeeple() &&
                                neighbor.getMeepleType() != null &&
                                neighbor.getMeepleType().equals("MONASTERY") &&
                                !neighbor.getMeepleOwner().equals(player.getColor())) {

                            String otherPlayerName = getPlayerNameByColor(neighbor.getMeepleOwner());
                            String message = "Рядом уже есть монастырь игрока " + otherPlayerName + "!";
                            System.out.println(message);
                            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                            return;
                        }
                    }
                }
            }

            tile.setMeeple(true, player.getColor(), "MONASTERY");
            objectOwners.put(objectKey, player.getColor());
            player.useMeeple();

            System.out.println("Мипл успешно поставлен на монастырь");
            broadcastChat(username + " поставил мипла на монастырь");

        } else if (tileType.equals("CITY")) {
            System.out.println("ОБРАБОТКА ГОРОДА ");

            if ("tile-n.png".equals(fileName)) {
                System.out.println("Обработка tile-n.png как города");

                boolean isConnectedToOther = false;
                int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

                for (int[] dir : directions) {
                    int nx = tile.getX() + dir[0];
                    int ny = tile.getY() + dir[1];

                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                        Tile neighbor = board[nx][ny];
                        if (neighbor != null && "tile-n.png".equals(neighbor.getFileName())) {
                            char side1 = tile.getSide(getDirectionChar(dir));
                            char side2 = neighbor.getSide(getOppositeDirectionChar(dir));

                            System.out.println("Проверка соединения с соседом в (" + nx + "," + ny + ")");
                            System.out.println("side1 (текущая): " + side1 + ", side2 (сосед): " + side2);

                            if (side1 == 'C' && side2 == 'C') {
                                isConnectedToOther = true;
                                System.out.println("tile-n.png уже соединен с другим tile-n.png!");
                                break;
                            }
                        }
                    }
                }

                if (isConnectedToOther) {
                    String message = "Эта городская плитка уже соединена с другой! Ждите завершения города.";
                    System.out.println(message);
                    getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                    return;
                }

                tile.setMeeple(true, player.getColor(), "CITY");
                String key = tile.getX() + "," + tile.getY() + ":CITY";
                objectOwners.put(key, player.getColor());
                player.useMeeple();

                broadcastChat(username + " поставил мипла на город ");

            } else {
                // Находим весь город
                List<Tile> cityTiles = findConnectedCity(tile.getX(), tile.getY());
                System.out.println("Найден город из " + cityTiles.size() + " плиток:");

                for (Tile cityTile : cityTiles) {
                    System.out.println("  Плитка в (" + cityTile.getX() + "," + cityTile.getY() +
                            "), файл: " + cityTile.getFileName() +
                            ", мипл: " + cityTile.hasMeeple() +
                            ", владелец: " + (cityTile.hasMeeple() ? cityTile.getMeepleOwner() : "нет"));
                }

                // Проверяем, занят ли город другим игроком
                String existingCityOwner = null;
                boolean hasConflict = false;

                for (Tile cityTile : cityTiles) {
                    if (cityTile.hasMeeple()) {
                        String tileOwner = cityTile.getMeepleOwner();
                        if (existingCityOwner == null) {
                            existingCityOwner = tileOwner;
                        } else if (!existingCityOwner.equals(tileOwner)) {
                            hasConflict = true;
                            break;
                        }
                    }
                }

                if (hasConflict) {
                    String message = "Город уже частично занят другим игроком!";
                    System.out.println(message);
                    getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                    return;
                }

                if (existingCityOwner != null && !existingCityOwner.equals(player.getColor())) {
                    String otherPlayerName = getPlayerNameByColor(existingCityOwner);
                    String message = "Город уже занят игроком " + otherPlayerName + "!";
                    System.out.println(message);
                    getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                    return;
                }

                tile.setMeeple(true, player.getColor(), "CITY");
                String key = tile.getX() + "," + tile.getY() + ":CITY";
                objectOwners.put(key, player.getColor());
                player.useMeeple();

                System.out.println("Мипл успешно поставлен на город");
                broadcastChat(username + " поставил мипла на город (" + cityTiles.size() + " плиток)");
            }

        } else {
            // Неизвестный тип
            String message = "Нельзя поставить мипла на эту плитку! Неизвестный тип: " + tileType;
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 9. Обновляем состояние и передаем ход
        broadcastAllScores();
        broadcastGameState();

        lastPlacedBy = null;
        lastPlacedX = -1;
        lastPlacedY = -1;

        System.out.println("Ход завершен, передаем ход следующему игроку");
        nextTurn();
    }



    public void skipTurn(String username) {
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

    public void endGame(String reason) {
        if (gameEnded) return;
        gameEnded = true;

        calculateFinalScores();
        String winnerMessage = determineWinner();
        String finalMessage = "ИГРА ОКОНЧЕНА \n" + reason + "\n\n" + winnerMessage;

        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage("GAME_END", "SERVER", finalMessage));
        }

        System.out.println("Игра окончена: " + reason);
    }

    private void calculateFinalScores() {

        Map<String, Integer> initialScores = new HashMap<>();
        for (Player player : playerInfo.values()) {
            initialScores.put(player.getName(), player.getScore());
            System.out.println(player.getName() + " начальные очки: " + player.getScore());
        }

        calculateRoadScores();
        calculateCityScores();
        calculateMonasteryScores();

        System.out.println("ИТОГОВЫЕ ОЧКИ ");
        for (Player player : playerInfo.values()) {
            int initial = initialScores.get(player.getName());
            int added = player.getScore() - initial;
            System.out.println(player.getName() + ": " + initial + " + " + added + " = " + player.getScore());
            broadcastScoreUpdate(player.getName());
        }
    }

    private void calculateRoadScores() {
        System.out.println("\nПОДСЧЕТ ОЧКОВ ЗА ДОРОГИ ");

        Set<String> visited = new HashSet<>();
        Set<String> processedSegments = new HashSet<>();
        Set<String> countedForks = new HashSet<>();

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                Tile tile = board[x][y];

                if (tile != null &&
                        tile.getType().equals("ROAD") &&
                        !"tile-l.png".equals(tile.getFileName()) &&
                        !visited.contains(x + "," + y)) {

                    List<Tile> segment = findCompleteRoadSegment(x, y);

                    if (segment.isEmpty()) continue;

                    // Генерируем уникальный ключ для сегмента
                    String segmentKey = generateSegmentKey(segment);

                    if (processedSegments.contains(segmentKey)) {
                        for (Tile roadTile : segment) {
                            visited.add(roadTile.getX() + "," + roadTile.getY());
                        }
                        continue;
                    }

                    System.out.println("\nОбработка сегмента дороги из " + segment.size() + " плиток:");
                    for (Tile roadTile : segment) {
                        System.out.println("  Плитка (" + roadTile.getX() + "," + roadTile.getY() +
                                "), мипл: " + roadTile.hasMeeple() +
                                ", владелец: " + (roadTile.hasMeeple() ? getPlayerNameByColor(roadTile.getMeepleOwner()) : "нет"));
                    }

                    String firstOwnerColor = null;

                    for (Tile roadTile : segment) {
                        if (roadTile.hasMeeple()) {
                            firstOwnerColor = roadTile.getMeepleOwner();
                            System.out.println("  Первый владелец всего сегмента: " +
                                    getPlayerNameByColor(firstOwnerColor) +
                                    " на плитке (" + roadTile.getX() + "," + roadTile.getY() + ")");
                            break;
                        }
                    }

                    // Если никто не занял дорогу - пропускаем
                    if (firstOwnerColor == null) {
                        System.out.println("  Вся дорога никем не занята");
                        for (Tile roadTile : segment) {
                            visited.add(roadTile.getX() + "," + roadTile.getY());
                        }
                        processedSegments.add(segmentKey);
                        continue;
                    }

                    Player firstOwner = getPlayerByColor(firstOwnerColor);
                    if (firstOwner == null) {
                        for (Tile roadTile : segment) {
                            visited.add(roadTile.getX() + "," + roadTile.getY());
                        }
                        processedSegments.add(segmentKey);
                        continue;
                    }

                    // ПОДСЧЕТ ОЧКОВ ЗА ВЕСЬ СЕГМЕНТ
                    int basePoints = segment.size();

                    int forkBonus = 0;
                    for (Tile roadTile : segment) {
                        forkBonus += countConnectedForks(roadTile, countedForks);
                    }

                    int totalPoints = basePoints + forkBonus;

                    System.out.println("  Всего плиток в сегменте: " + segment.size());
                    System.out.println("  Базовые очки: " + basePoints);
                    System.out.println("  Бонус за развилки: " + forkBonus);
                    System.out.println("  Всего очков для первого владельца: " + totalPoints);

                    // НАЧИСЛЯЕМ ОЧКИ ТОЛЬКО ПЕРВОМУ ВЛАДЕЛЬЦУ
                    firstOwner.addScore(totalPoints);

                    // ВОЗВРАЩАЕМ МИПЛЫ ТОЛЬКО ПЕРВОГО ВЛАДЕЛЬЦА
                    for (Tile roadTile : segment) {
                        if (roadTile.hasMeeple() &&
                                roadTile.getMeepleOwner().equals(firstOwnerColor)) {
                            roadTile.setMeeple(false, null, null);
                            firstOwner.returnMeeple();
                        }
                    }

                    String ownerName = getPlayerNameByColor(firstOwnerColor);
                    broadcastChat("Дорога (" + segment.size() + " плиток): " +
                            ownerName + " +" + totalPoints + " очков");

                    // Помечаем как обработанные
                    processedSegments.add(segmentKey);
                    for (Tile roadTile : segment) {
                        visited.add(roadTile.getX() + "," + roadTile.getY());
                    }
                }
            }
        }
    }
    private int countConnectedForks(Tile roadTile, Set<String> countedForks) {
        int forkCount = 0;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int nx = roadTile.getX() + dir[0];
            int ny = roadTile.getY() + dir[1];

            if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                Tile neighbor = board[nx][ny];
                if (neighbor != null && "tile-l.png".equals(neighbor.getFileName())) {
                    char tileSide = roadTile.getSide(getDirectionChar(dir));
                    char forkSide = neighbor.getSide(getOppositeDirectionChar(dir));

                    if (tileSide == 'S' && forkSide == 'S') {
                        String forkKey = nx + "," + ny;
                        if (!countedForks.contains(forkKey)) {
                            forkCount++;
                            countedForks.add(forkKey);
                            System.out.println("  + Развилка в (" + nx + "," + ny + ") учтена");
                        } else {
                            System.out.println("  - Развилка в (" + nx + "," + ny + ") уже учтена ранее");
                        }
                    }
                }
            }
        }

        return forkCount;
    }

    private List<Tile> findCompleteRoadSegment(int startX, int startY) {
        List<Tile> segment = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<int[]> queue = new LinkedList<>();

        queue.add(new int[]{startX, startY});

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            String key = x + "," + y;

            if (visited.contains(key)) continue;

            Tile tile = board[x][y];
            if (tile == null) continue;

            if (!tile.getType().equals("ROAD") ||
                    "tile-l.png".equals(tile.getFileName())) {
                continue;
            }

            visited.add(key);
            segment.add(tile);

            // Ищем всех соседей-дорог
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                    Tile neighbor = board[nx][ny];
                    if (neighbor == null) continue;

                    if ("tile-l.png".equals(neighbor.getFileName())) {
                        // Развилка останавливает поиск в этом направлении
                        continue;
                    }

                    if (!neighbor.getType().equals("ROAD")) continue;

                    String neighborKey = nx + "," + ny;
                    if (visited.contains(neighborKey)) continue;

                    char side1 = tile.getSide(getDirectionChar(dir));
                    char side2 = neighbor.getSide(getOppositeDirectionChar(dir));

                    if (side1 == 'S' && side2 == 'S') {
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }

        return segment;
    }
    private String generateSegmentKey(List<Tile> segment) {
        List<String> positions = new ArrayList<>();
        for (Tile tile : segment) {
            positions.add(tile.getX() + "," + tile.getY());
        }
        Collections.sort(positions);
        return String.join("|", positions);
    }

    private List<Tile> findCurrentRoadSegmentOnly(int startX, int startY) {
        List<Tile> segment = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<int[]> queue = new LinkedList<>();

        queue.add(new int[]{startX, startY});

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            String key = x + "," + y;

            if (visited.contains(key)) continue;

            Tile tile = board[x][y];
            if (tile == null) continue;

            boolean isRoad = tile.getType().equals("ROAD");
            boolean isSpecialTile = "tile-l.png".equals(tile.getFileName()) ||
                    "tile-n.png".equals(tile.getFileName()) ||
                    "tile-monastery.png".equals(tile.getFileName());

            if (!isRoad || isSpecialTile) continue;

            visited.add(key);
            segment.add(tile);

            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                    Tile neighbor = board[nx][ny];
                    if (neighbor != null && !visited.contains(nx + "," + ny)) {

                        boolean neighborIsRoad = neighbor.getType().equals("ROAD");
                        boolean neighborIsSpecial = "tile-l.png".equals(neighbor.getFileName()) ||
                                "tile-n.png".equals(neighbor.getFileName()) ||
                                "tile-monastery.png".equals(neighbor.getFileName());

                        if (neighborIsRoad && !neighborIsSpecial) {
                            // Проверяем соединение
                            char side1 = tile.getSide(getDirectionChar(dir));
                            char side2 = neighbor.getSide(getOppositeDirectionChar(dir));

                            if (areSidesCompatible(side1, side2, tile, neighbor)) {
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }
                }
            }
        }

        return segment;
    }

    private void calculateMonasteryScores() {
        System.out.println("Подсчет очков за монастыри...");

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                Tile tile = board[x][y];
                if (tile != null && "tile-monastery.png".equals(tile.getFileName()) && tile.hasMeeple()) {
                    String ownerColor = tile.getMeepleOwner();
                    Player player = getPlayerByColor(ownerColor);
                    if (player != null) {
                        int neighbors = countNeighbors(x, y);
                        int points = 1 + neighbors;
                        player.addScore(points);

                        broadcastChat("Монастырь (" + neighbors + " соседей): " +
                                player.getName() + " +" + points + " очков");

                        // Возвращаем мипл
                        tile.setMeeple(false, null, null);
                        player.returnMeeple();
                    }
                }
            }
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
    private void calculateCityScores() {
        System.out.println("\n=== ПОДСЧЕТ ОЧКОВ ЗА ГОРОДА ===");
        Set<String> visited = new HashSet<>();

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                Tile tile = board[x][y];
                if (tile != null && tile.getType().equals("CITY") && !visited.contains(x + "," + y)) {

                    List<Tile> city = findConnectedCity(x, y);
                    boolean isTileNCity = false;

                    // Проверяем, не завершен ли город
                    for (Tile cityTile : city) {
                        if ("tile-n.png".equals(cityTile.getFileName())) {
                            isTileNCity = true;
                            break;
                        }
                    }

                    // Отладка ДО проверки alreadyCompleted
                    System.out.println("\nНайден город из " + city.size() + " плиток:");
                    for (Tile cityTile : city) {
                        System.out.println("  (" + cityTile.getX() + "," + cityTile.getY() +
                                ") - " + cityTile.getFileName());
                    }

                    if (isTileNCity) {
                        boolean alreadyCompleted = false;
                        for (Tile cityTile : city) {
                            for (String completedKey : completedCities.keySet()) {
                                if (completedKey.contains(cityTile.getX() + "," + cityTile.getY())) {
                                    alreadyCompleted = true;
                                    System.out.println("Плитка уже в completedCities: " + completedKey);
                                    break;
                                }
                            }
                            if (alreadyCompleted) break;
                        }
                        if (alreadyCompleted) {
                            System.out.println("Пропускаем tile-n.png город - уже учтен");
                            broadcastChat("Завершенный город (tile-n.png) уже учтен ранее");
                            continue;
                        }
                    }

                    // Подсчет очков за город
                    Map<String, Integer> ownerCount = new HashMap<>();
                    for (Tile cityTile : city) {
                        if (cityTile.hasMeeple()) {
                            String owner = cityTile.getMeepleOwner();
                            ownerCount.put(owner, ownerCount.getOrDefault(owner, 0) + 1);
                            System.out.println("  Мипл на плитке: владелец=" + getPlayerNameByColor(owner));
                        }
                    }

                    // Критически важная проверка!
                    boolean isCompleted = isCityCompleted(city);
                    int pointsPerTile = isCompleted ? 2 : 1;
                    int totalPoints = city.size() * pointsPerTile;

                    System.out.println("Результат проверки завершенности: " + (isCompleted ? "ЗАВЕРШЕН" : "НЕЗАВЕРШЕН"));
                    System.out.println("Очков за плитку: " + pointsPerTile);
                    System.out.println("Всего очков: " + totalPoints);

                    if (!ownerCount.isEmpty()) {
                        // Определяем владельца
                        String majorityOwner = null;
                        int maxCount = 0;
                        boolean tie = false;

                        for (Map.Entry<String, Integer> entry : ownerCount.entrySet()) {
                            System.out.println("  Игрок " + getPlayerNameByColor(entry.getKey()) +
                                    ": " + entry.getValue() + " миплов");
                            if (entry.getValue() > maxCount) {
                                maxCount = entry.getValue();
                                majorityOwner = entry.getKey();
                                tie = false;
                            } else if (entry.getValue() == maxCount) {
                                tie = true;
                            }
                        }

                        String status = isCompleted ? "Завершенный город" : "Незавершенный город";
                        System.out.println("Статус: " + status);

                        if (!tie && majorityOwner != null) {
                            Player player = getPlayerByColor(majorityOwner);
                            if (player != null) {
                                player.addScore(totalPoints);
                                System.out.println("Начисляем " + totalPoints + " очков игроку " + player.getName());

                                broadcastChat(status + " (" + city.size() + " плиток): " +
                                        player.getName() + " +" + totalPoints + " очков");

                                // Возвращаем миплы ТОЛЬКО для завершенных городов
                                if (isCompleted) {
                                    int returnedMeeples = 0;
                                    for (Tile cityTile : city) {
                                        if (cityTile.hasMeeple() &&
                                                cityTile.getMeepleOwner().equals(majorityOwner)) {
                                            cityTile.setMeeple(false, null, null);
                                            player.returnMeeple();
                                            returnedMeeples++;
                                        }
                                    }
                                    System.out.println("Возвращено миплов: " + returnedMeeples);
                                }
                            }
                        } else if (tie) {
                            // Ничья - делим очки поровну
                            int pointsEach = totalPoints / ownerCount.size();
                            System.out.println("НИЧЬЯ! Каждый получает по " + pointsEach + " очков");

                            for (String owner : ownerCount.keySet()) {
                                Player player = getPlayerByColor(owner);
                                if (player != null) {
                                    player.addScore(pointsEach);
                                    broadcastChat(status + " (разделен): " + player.getName() +
                                            " +" + pointsEach + " очков");

                                    // Возвращаем миплы для завершенных городов
                                    if (isCompleted) {
                                        for (Tile cityTile : city) {
                                            if (cityTile.hasMeeple() &&
                                                    cityTile.getMeepleOwner().equals(owner)) {
                                                cityTile.setMeeple(false, null, null);
                                                player.returnMeeple();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        String status = isCompleted ? "Завершенный город" : "Незавершенный город";
                        System.out.println("Нет миплов в городе → 0 очков");
                        broadcastChat(status + " (" + city.size() + " плиток): нет миплов → 0 очков");
                    }

                    // Отмечаем все плитки как посещенные
                    for (Tile cityTile : city) {
                        visited.add(cityTile.getX() + "," + cityTile.getY());
                    }
                }
            }
        }
        System.out.println(" ЗАВЕРШЕН ПОДСЧЕТ ГОРОДОВ \n");
    }
    private boolean isCityCompleted(List<Tile> city) {
        for (Tile tile : city) {
            int x = tile.getX();
            int y = tile.getY();

            char[] sides = {'N', 'E', 'S', 'W'};
            int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

            for (int i = 0; i < 4; i++) {
                char side = sides[i];
                int[] dir = directions[i];

                if (tile.getSide(side) == 'C') {
                    int nx = x + dir[0];
                    int ny = y + dir[1];

                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                        Tile neighbor = board[nx][ny];

                        if (neighbor == null) {
                            return false;
                        }

                        char oppositeSide = getOppositeSide(side);
                        if (neighbor.getSide(oppositeSide) != 'C') {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private List<Tile> findConnectedCity(int startX, int startY) {
        List<Tile> connected = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<int[]> queue = new LinkedList<>();

        queue.add(new int[]{startX, startY});

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            Tile tile = board[x][y];

            if (tile == null || !tile.getType().equals("CITY")) continue;

            String key = x + "," + y;
            if (visited.contains(key)) continue;

            visited.add(key);
            connected.add(tile);

            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                    Tile neighbor = board[nx][ny];
                    if (neighbor != null && neighbor.getType().equals("CITY")) {
                        char side1 = tile.getSide(getDirectionChar(dir));
                        char side2 = neighbor.getSide(getOppositeDirectionChar(dir));
                        if (side1 == 'C' && side2 == 'C') {
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
            }
        }

        return connected;
    }
    private void broadcastAllScores() {
        for (Player player : playerInfo.values()) {
            for (ClientHandler p : players) {
                p.sendMessage(new GameMessage("SCORE_UPDATE", "SERVER", player));
            }
        }
    }

    private String getPlacementOptionsMessage(Tile tile, String username) {
        StringBuilder message = new StringBuilder();


        String tileType;
        String fileName = tile.getFileName();

        if ("tile-n.png".equals(fileName)) {
            tileType = "CITY";
        } else if ("tile-l.png".equals(fileName)) {
            tileType = "FORK"; // развилка
        } else if ("tile-monastery.png".equals(fileName)) {
            tileType = "MONASTERY";
        } else {
            tileType = tile.getType(); // для остальных плиток
        }

        message.append("Плитка размещена! Тип: ").append(tileType);

        Player player = playerInfo.get(username);
        if (player == null) {
            return message.toString();
        }

        // ОБРАБОТКА РАЗНЫХ ТИПОВ ПЛИТОК
        if ("tile-l.png".equals(fileName)) {
            // РАЗВИЛКА - нельзя ставить мипла
            message.append(" (развилка дорог). ");
            message.append("На эту плитку нельзя ставить мипла. ");
            message.append("Пропустите ход (S)");

        } else if ("tile-n.png".equals(fileName)) {
            message.append(" (часть города). ");


            boolean isConnectedToOther = false;
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

            for (int[] dir : directions) {
                int nx = tile.getX() + dir[0];
                int ny = tile.getY() + dir[1];

                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                    Tile neighbor = board[nx][ny];
                    if (neighbor != null && "tile-n.png".equals(neighbor.getFileName())) {
                        char side1 = tile.getSide(getDirectionChar(dir));
                        char side2 = neighbor.getSide(getOppositeDirectionChar(dir));

                        if (side1 == 'C' && side2 == 'C') {
                            isConnectedToOther = true;
                            break;
                        }
                    }
                }
            }

            if (isConnectedToOther) {
                message.append("Эта плитка уже соединена с другой частью города! ");
                message.append("Ждите завершения города для получения очков. ");
                message.append("Пропустите ход (S)");
            } else if (player.getMeeplesLeft() > 0) {
                message.append("Вы можете поставить мипла на город (M) или пропустить ход (S)");
            } else {
                message.append("У вас не осталось миплов! Пропустите ход (S)");
            }

        } else if (tile.canPlaceMeeple()) {
            // ПЛИТКИ, НА КОТОРЫЕ МОЖНО СТАВИТЬ МИПЛА

            if (tileType.equals("ROAD")) {
                message.append(" (дорога). ");

                List<Tile> currentSegment = findCurrentRoadSegmentOnly(tile.getX(), tile.getY());

                // Проверяем ТОЛЬКО текущий сегмент дороги
                boolean roadOccupiedByOther = false;
                String otherPlayerName = null;

                for (Tile roadTile : currentSegment) {
                    if (roadTile.hasMeeple() && !roadTile.getMeepleOwner().equals(player.getColor())) {
                        roadOccupiedByOther = true;
                        otherPlayerName = getPlayerNameByColor(roadTile.getMeepleOwner());
                        break;
                    }
                }

                if (roadOccupiedByOther) {
                    message.append("НЕВОЗМОЖНО поставить мипл - эта часть дороги уже занята игроком ")
                            .append(otherPlayerName)
                            .append("! ");
                    message.append("Пропустите ход (S)");

                } else if (player.getMeeplesLeft() <= 0) {
                    message.append("У вас не осталось миплов! Пропустите ход (S)");

                } else {
                    // Проверяем, нет ли уже своего мипла в этом сегменте
                    boolean alreadyHasOwnMeeple = false;
                    for (Tile roadTile : currentSegment) {
                        if ("tile-l.png".equals(roadTile.getFileName())) {
                            continue;
                        }

                        if (roadTile.hasMeeple() && roadTile.getMeepleOwner().equals(player.getColor())) {
                            alreadyHasOwnMeeple = true;
                            break;
                        }
                    }

                    if (alreadyHasOwnMeeple) {
                        message.append("Вы уже поставили мипла на эту часть дороги! ");
                        message.append("Пропустите ход (S)");
                    } else {
                        // Считаем плитки дороги и развилки
                        int roadTiles = 0;
                        int forks = 0;
                        for (Tile roadTile : currentSegment) {
                            if ("tile-l.png".equals(roadTile.getFileName())) {
                                forks++;
                            } else {
                                roadTiles++;
                            }
                        }

                        // Проверяем, соединена ли дорога с развилкой
                        boolean connectedToFork = false;
                        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

                        for (int[] dir : directions) {
                            int nx = tile.getX() + dir[0];
                            int ny = tile.getY() + dir[1];

                            if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                                Tile neighbor = board[nx][ny];
                                if (neighbor != null && "tile-l.png".equals(neighbor.getFileName())) {
                                    char tileSide = tile.getSide(getDirectionChar(dir));
                                    char forkSide = neighbor.getSide(getOppositeDirectionChar(dir));

                                    if (tileSide == 'S' && forkSide == 'S') {
                                        connectedToFork = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (connectedToFork) {
                            message.append("ЭТА ДОРОГА СОЕДИНЕНА С РАЗВИЛКОЙ! ");
                        }

                        message.append("Вы можете поставить мипла на дорогу (M) ");
                        message.append("(").append(roadTiles).append(" плиток дороги");
                        if (forks > 0) {
                            message.append(", через ").append(forks).append(" развилок");
                        }
                        message.append(") или пропустить ход ");
                    }
                }

            } else if (tileType.equals("MONASTERY")) {
                // МОНАСТЫРЯ
                message.append(" (монастырь). ");

                // Проверяем, не занят ли уже этот монастырь
                String objectKey = tile.getX() + "," + tile.getY() + ":MONASTERY";
                String existingOwner = objectOwners.get(objectKey);

                if (existingOwner != null && !existingOwner.equals(player.getColor())) {
                    String otherPlayerName = getPlayerNameByColor(existingOwner);
                    message.append("Монастырь уже занят игроком ").append(otherPlayerName).append("! ");
                    message.append("Пропустите ход (S)");
                } else if (player.getMeeplesLeft() <= 0) {
                    message.append("У вас не осталось миплов! Пропустите ход (S)");
                } else {
                    // Проверяем соседние монастыри
                    boolean hasNearbyMonastery = false;
                    String nearbyPlayerName = null;

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;

                            int nx = tile.getX() + dx;
                            int ny = tile.getY() + dy;

                            if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                                Tile neighbor = board[nx][ny];
                                if (neighbor != null && neighbor.hasMeeple() &&
                                        neighbor.getMeepleType() != null &&
                                        neighbor.getMeepleType().equals("MONASTERY") &&
                                        !neighbor.getMeepleOwner().equals(player.getColor())) {

                                    hasNearbyMonastery = true;
                                    nearbyPlayerName = getPlayerNameByColor(neighbor.getMeepleOwner());
                                    break;
                                }
                            }
                        }
                        if (hasNearbyMonastery) break;
                    }

                    if (hasNearbyMonastery) {
                        message.append("Рядом уже есть монастырь игрока ").append(nearbyPlayerName).append("! ");
                        message.append("Пропустите ход ");
                    } else {
                        message.append("Вы можете поставить мипла на монастырь  или пропустить ход ");
                    }
                }

            } else if (tileType.equals("CITY")) {
                //ОБРАБОТКА ГОРОДА
                message.append(" (город). ");

                if (player.getMeeplesLeft() <= 0) {
                    message.append("У вас не осталось миплов! Пропустите ход (S)");
                } else {
                    // Находим весь город
                    List<Tile> cityTiles = findConnectedCity(tile.getX(), tile.getY());

                    // Проверяем, занят ли город другим игроком
                    String existingCityOwner = null;
                    boolean hasConflict = false;

                    for (Tile cityTile : cityTiles) {
                        if (cityTile.hasMeeple()) {
                            String tileOwner = cityTile.getMeepleOwner();
                            if (existingCityOwner == null) {
                                existingCityOwner = tileOwner;
                            } else if (!existingCityOwner.equals(tileOwner)) {
                                hasConflict = true;
                                break;
                            }
                        }
                    }

                    if (hasConflict) {
                        message.append("Город уже частично занят разными игроками! ");
                        message.append("Пропустите ход ");
                    } else if (existingCityOwner != null && !existingCityOwner.equals(player.getColor())) {
                        String otherPlayerName = getPlayerNameByColor(existingCityOwner);
                        message.append("Город уже занят игроком ").append(otherPlayerName).append("! ");
                        message.append("Пропустите ход ");
                    } else {
                        message.append("Вы можете поставить мипла на город  ");
                        message.append("(").append(cityTiles.size()).append(" плиток) или пропустить ход ");
                    }
                }

            } else {
                // НЕИЗВЕСТНЫЙ ТИП
                message.append(". Нельзя поставить мипла на эту плитку. Пропустите ход ");
            }

        } else {
            // НЕЛЬЗЯ СТАВИТЬ МИПЛА
            message.append(". Нельзя поставить мипла на эту плитку. Пропустите ход ");
        }

        return message.toString();
    }


    private void broadcastGameState() {
        Tile[][] boardCopy = new Tile[15][15];

        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[i].length; j++) {
                Tile original = board[i][j];
                if (original != null) {
                    Tile copy = new Tile(original.getId(), original.getImagePath(), original.getFeatures());
                    copy.setX(original.getX());
                    copy.setY(original.getY());
                    copy.setRotation(original.getRotation());

                    if (original.hasMeeple()) {
                        copy.setMeeple(true, original.getMeepleOwner(), original.getMeepleType());
                    }

                    boardCopy[i][j] = copy;
                }
            }
        }

        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage("GAME_STATE", "SERVER", boardCopy));
        }
    }

    private void broadcastScoreUpdate(String username) {
        Player player = playerInfo.get(username);
        if (player != null) {
            for (ClientHandler p : players) {
                p.sendMessage(new GameMessage("SCORE_UPDATE", "SERVER", player));
            }
        }
    }

    public void broadcastChat(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
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

    private Player getPlayerByColor(String color) {
        for (Player player : playerInfo.values()) {
            if (player.getColor().equals(color)) {
                return player;
            }
        }
        return null;
    }

    private String getPlayerNameByColor(String color) {
        for (Player player : playerInfo.values()) {
            if (player.getColor().equals(color)) {
                return player.getName();
            }
        }
        return "неизвестный игрок";
    }

    public int getPlayerCount() {
        return players.size();
    }
}