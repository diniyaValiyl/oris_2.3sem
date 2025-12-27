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

        // Города (tile-n.png) - 6 штук
        for (int i = 0; i < 6; i++) {
            allTiles.add(new String[]{"tile-n.png", "N=C S=N W=N E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"});
        }

        // Монастыри (tile-monastery.png) - 3 штуки
        for (int i = 0; i < 3; i++) {
            allTiles.add(new String[]{"tile-monastery.png", "N=N S=N W=N E=N NS=0 NE=0 NW=0 WE=0 SE=0 SW=0 CL=1"});
        }

        // Развилки (tile-l.png) - 2 штуки
        for (int i = 0; i < 2; i++) {
            allTiles.add(new String[]{"tile-l.png", "N=N S=S W=S E=S NS=0 NE=0 NW=0 WE=0 SE=0 SW=0"});
        }

        // Дороги (9 штук)
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

        // Отладочная информация
        Map<String, Integer> tileCount = new HashMap<>();
        for (Tile tile : deck) {
            String fileName = tile.getFileName();
            tileCount.put(fileName, tileCount.getOrDefault(fileName, 0) + 1);
        }

        System.out.println("=== СОСТАВ КОЛОДЫ ===");
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
        // Особый случай для tile-n.png
        if (tile1.isTileN() || tile2.isTileN()) {
            if (side1 == 'C' && side2 == 'C') return true;
            if (side1 == 'N' && side2 == 'N') return true;
            return false;
        }

        // Особый случай для развилки tile-l.png
        if (tile1.isTileL() || tile2.isTileL()) {
            // Развилка соединяется с дорогами только через стороны 'S'
            if (side1 == 'S' && side2 == 'S') return true; // дорога+дорога
            if (side1 == 'N' && side2 == 'N') return true; // поле+поле
            // Развилка НЕ соединяет дорогу с полем
            return false;
        }

        // Общие правила
        if (side1 == 'C' && side2 == 'C') return true;
        if (side1 == 'S' && side2 == 'S') return true;
        if (side1 == 'N' && side2 == 'N') return true;

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

        System.out.println("=== РАЗМЕЩЕНИЕ ПЛИТКИ ===");
        System.out.println(username + " пытается разместить " + tile.getFileName() + " в (" + x + "," + y + ")");

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
    /**
     * Находит ВСЕ сегменты дорог, связанные через развилки
     * Например: [Дорога1]-[Дорога2]-[Развилка]-[Дорога3] → два сегмента: [Дорога1, Дорога2] и [Дорога3]
     */
    private List<List<Tile>> findAllRoadSegmentsThroughFork(int startX, int startY) {
        List<List<Tile>> allSegments = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // Начинаем с начальной плитки
        List<Tile> initialSegment = findRoadSegmentUntilFork(startX, startY, visited);
        if (!initialSegment.isEmpty()) {
            allSegments.add(initialSegment);
        }

        // Для каждой развилки в начальном сегменте ищем дополнительные сегменты
        for (Tile tile : initialSegment) {
            if ("tile-l.png".equals(tile.getFileName())) {
                findAdditionalSegmentsFromFork(tile.getX(), tile.getY(), visited, allSegments);
            }
        }

        System.out.println("Найдено всего " + allSegments.size() + " сегментов дороги");
        return allSegments;
    }

    /**
     * Находит сегмент дороги ДО развилки (развилка - конец сегмента)
     */
    private List<Tile> findRoadSegmentUntilFork(int startX, int startY, Set<String> visited) {
        List<Tile> segment = new ArrayList<>();
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

            // Проверяем, является ли плитка частью дорожной системы
            boolean isRoadRelated = tile.getType().equals("ROAD") ||
                    "tile-l.png".equals(tile.getFileName());

            if (!isRoadRelated) continue;

            visited.add(key);
            segment.add(tile);

            // Если это развилка - останавливаемся здесь (не ищем дальше от развилки)
            if ("tile-l.png".equals(tile.getFileName())) {
                continue;
            }

            // Для обычных дорог ищем соседей
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                    Tile neighbor = board[nx][ny];
                    if (neighbor != null && !visited.contains(nx + "," + ny)) {

                        // Проверяем, является ли сосед частью дорожной системы
                        boolean neighborIsRoadRelated = neighbor.getType().equals("ROAD") ||
                                "tile-l.png".equals(neighbor.getFileName());

                        if (neighborIsRoadRelated) {
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

    /**
     * Находит дополнительные сегменты, исходящие от развилки
     */
    private void findAdditionalSegmentsFromFork(int forkX, int forkY, Set<String> visited, List<List<Tile>> allSegments) {
        Tile fork = board[forkX][forkY];
        if (fork == null || !"tile-l.png".equals(fork.getFileName())) return;

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int nx = forkX + dir[0];
            int ny = forkY + dir[1];
            String neighborKey = nx + "," + ny;

            if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && !visited.contains(neighborKey)) {
                Tile neighbor = board[nx][ny];

                if (neighbor != null) {
                    char forkSide = fork.getSide(getDirectionChar(dir));
                    char neighborSide = neighbor.getSide(getOppositeDirectionChar(dir));

                    // Развилка соединяется дорогой только если обе стороны 'S' (дорога)
                    if (forkSide == 'S' && neighborSide == 'S') {
                        boolean neighborIsRoadRelated = neighbor.getType().equals("ROAD") ||
                                "tile-l.png".equals(neighbor.getFileName());

                        if (neighborIsRoadRelated) {
                            // Находим новый сегмент, начиная с соседа
                            List<Tile> newSegment = findRoadSegmentUntilFork(nx, ny, visited);
                            if (!newSegment.isEmpty()) {
                                allSegments.add(newSegment);

                                // Рекурсивно проверяем развилки в новом сегменте
                                for (Tile tile : newSegment) {
                                    if ("tile-l.png".equals(tile.getFileName()) &&
                                            !visited.contains(tile.getX() + "," + tile.getY())) {
                                        findAdditionalSegmentsFromFork(tile.getX(), tile.getY(), visited, allSegments);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Вспомогательный метод для получения символа направления
     */
    private char getDirectionChar(int[] dir) {
        if (dir[0] == -1) return 'W';
        if (dir[0] == 1) return 'E';
        if (dir[1] == -1) return 'N';
        if (dir[1] == 1) return 'S';
        return 'N';
    }

    /**
     * Вспомогательный метод для получения противоположного символа направления
     */
    private char getOppositeDirectionChar(int[] dir) {
        if (dir[0] == -1) return 'E';
        if (dir[0] == 1) return 'W';
        if (dir[1] == -1) return 'S';
        if (dir[1] == 1) return 'N';
        return 'S';
    }

    /**
     * Вспомогательный метод для получения противоположной стороны
     */
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

        if (completedCities.containsKey(cityKey) || completedCities.containsKey(reverseKey)) {
            return;
        }

        completedCities.put(cityKey, true);
        String cityOwner = findCityOwner(x1, y1, x2, y2);

        if ("DIVIDED".equals(cityOwner)) {
            handleDividedCity(x1, y1, x2, y2);
            return;
        }

        if (cityOwner == null) {
            broadcastChat("Город завершен, но никто не владеет им!");
            return;
        }

        Player player = playerInfo.get(cityOwner);
        if (player == null) return;

        int points = 4;
        player.addScore(points);
        returnMeeplesForCity(x1, y1, x2, y2, cityOwner);

        broadcastChat("=== ГОРОД ЗАВЕРШЕН! ===");
        broadcastChat("Город из двух плиток tile-n.png завершен!");
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

        broadcastChat("=== ГОРОД ЗАВЕРШЕН (РАЗДЕЛЕН)! ===");
        broadcastChat("Город разделен между " + player1.getName() + " и " + player2.getName());
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
        System.out.println("=== PLACE MEEPLE CALLED ===");
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

        System.out.println("Плитка: " + tile.getFileName());
        System.out.println("getType(): " + tile.getType());
        System.out.println("canPlaceMeeple(): " + tile.canPlaceMeeple());
        System.out.println("hasMeeple(): " + tile.hasMeeple());
        System.out.println("У игрока миплов осталось: " + player.getMeeplesLeft());

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

            // ИСПРАВЛЕННАЯ ЛОГИКА: находим ТОЛЬКО текущий сегмент (до развилок)
            List<Tile> currentSegment = findCurrentRoadSegmentOnly(tile.getX(), tile.getY());
            System.out.println("Текущий сегмент: " + currentSegment.size() + " плиток");

            // Отладочный вывод
            for (Tile roadTile : currentSegment) {
                System.out.println("  (" + roadTile.getX() + "," + roadTile.getY() +
                        ") - " + roadTile.getFileName() +
                        ", мипл: " + roadTile.hasMeeple() +
                        ", владелец: " + (roadTile.hasMeeple() ? roadTile.getMeepleOwner() : "нет"));
            }

            // Проверяем ТОЛЬКО в текущем сегменте наличие миплов других игроков
            boolean roadOccupiedByOther = false;
            String otherPlayerName = null;

            for (Tile roadTile : currentSegment) {
                // Пропускаем развилки (на них нет миплов)
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

            // Проверяем, не поставил ли игрок уже мипл на ЭТУ ЧАСТЬ дороги
            boolean roadAlreadyHasOwnMeeple = false;
            for (Tile roadTile : currentSegment) {
                if ("tile-l.png".equals(roadTile.getFileName())) {
                    continue; // Пропускаем развилки
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

            // ВСЕ проверки пройдены - ставим мипл
            tile.setMeeple(true, player.getColor(), "ROAD");
            String key = tile.getX() + "," + tile.getY() + ":ROAD";
            objectOwners.put(key, player.getColor());
            player.useMeeple();

            // Подсчитываем статистику для сообщения
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
            String broadcastMsg = username + " поставил мипла на дорогу (" + roadTilesInSegment + " плиток";
            if (forksInSegment > 0) {
                broadcastMsg += ", через " + forksInSegment + " развилок";
            }
            broadcastMsg += ")";
            broadcastChat(broadcastMsg);

        } else if (tileType.equals("MONASTERY")) {
            System.out.println("=== ОБРАБОТКА МОНАСТЫРЯ ===");

            String objectKey = tile.getX() + "," + tile.getY() + ":MONASTERY";
            String existingOwner = objectOwners.get(objectKey);

            // Проверяем, не занят ли этот монастырь
            if (existingOwner != null && !existingOwner.equals(player.getColor())) {
                String otherPlayerName = getPlayerNameByColor(existingOwner);
                String message = "Монастырь уже занят игроком " + otherPlayerName + "!";
                System.out.println(message);
                getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
                return;
            }

            // Проверяем соседние монастыри (в радиусе 1 клетки)
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

            // ВСЕ проверки пройдены - ставим мипл
            tile.setMeeple(true, player.getColor(), "MONASTERY");
            objectOwners.put(objectKey, player.getColor());
            player.useMeeple();

            System.out.println("Мипл успешно поставлен на монастырь");
            broadcastChat(username + " поставил мипла на монастырь");

        } else if (tileType.equals("CITY")) {
            System.out.println("=== ОБРАБОТКА ГОРОДА ===");

            if ("tile-n.png".equals(fileName)) {
                System.out.println("Обработка tile-n.png как города");

                // Для tile-n.png проверяем, не соединен ли он уже с другим tile-n.png
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

                // ВСЕ проверки пройдены - ставим мипл на tile-n.png
                tile.setMeeple(true, player.getColor(), "CITY");
                String key = tile.getX() + "," + tile.getY() + ":CITY";
                objectOwners.put(key, player.getColor());
                player.useMeeple();

                System.out.println("Мипл успешно поставлен на tile-n.png (город)");
                broadcastChat(username + " поставил мипла на город (плитка tile-n.png)");

            } else {
                // Для обычных городов (не tile-n.png)
                System.out.println("Обработка обычного города");

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

                // ВСЕ проверки пройдены - ставим мипл
                tile.setMeeple(true, player.getColor(), "CITY");
                String key = tile.getX() + "," + tile.getY() + ":CITY";
                objectOwners.put(key, player.getColor());
                player.useMeeple();

                System.out.println("Мипл успешно поставлен на город");
                broadcastChat(username + " поставил мипла на город (" + cityTiles.size() + " плиток)");
            }

        } else {
            // Неизвестный тип плитки
            String message = "Нельзя поставить мипла на эту плитку! Неизвестный тип: " + tileType;
            System.out.println(message);
            getPlayer(username).sendMessage(new GameMessage("CHAT_MESSAGE", "SERVER", message));
            return;
        }

        // 9. Обновляем состояние и передаем ход
        broadcastAllScores(); // ИСПРАВЛЕНО: отправляем очки всем
        broadcastGameState();

        // Сбрасываем информацию о последней размещенной плитке
        lastPlacedBy = null;
        lastPlacedX = -1;
        lastPlacedY = -1;

        System.out.println("Ход завершен, передаем ход следующему игроку");
        nextTurn();
    }

    private List<Tile> findConnectedRoadSegment(int startX, int startY) {
        return findRoadSegmentUntilFork(startX, startY, new HashSet<>());
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
        String finalMessage = "=== ИГРА ОКОНЧЕНА ===\n" + reason + "\n\n" + winnerMessage;

        for (ClientHandler player : players) {
            player.sendMessage(new GameMessage("GAME_END", "SERVER", finalMessage));
        }

        System.out.println("Игра окончена: " + reason);
    }

    private void calculateFinalScores() {
        System.out.println("=== ПОДСЧЕТ ФИНАЛЬНЫХ ОЧКОВ ===");

        // Сохраняем начальные очки
        Map<String, Integer> initialScores = new HashMap<>();
        for (Player player : playerInfo.values()) {
            initialScores.put(player.getName(), player.getScore());
            System.out.println(player.getName() + " начальные очки: " + player.getScore());
        }

        // Подсчет очков в правильном порядке
        calculateRoadScores();
        calculateCityScores();
        calculateMonasteryScores();

        // Выводим итоги
        System.out.println("=== ИТОГОВЫЕ ОЧКИ ===");
        for (Player player : playerInfo.values()) {
            int initial = initialScores.get(player.getName());
            int added = player.getScore() - initial;
            System.out.println(player.getName() + ": " + initial + " + " + added + " = " + player.getScore());
            broadcastScoreUpdate(player.getName());
        }
    }

    private void calculateRoadScores() {
        System.out.println("=== ПОДСЧЕТ ОЧКОВ ЗА ДОРОГИ ===");
        Set<String> visited = new HashSet<>();

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                Tile tile = board[x][y];

                // Ищем только ОБЫЧНЫЕ ДОРОГИ (не развилки)
                if (tile != null &&
                        tile.getType().equals("ROAD") &&
                        !"tile-l.png".equals(tile.getFileName()) &&
                        !visited.contains(x + "," + y)) {

                    System.out.println("\nНачинаем подсчет дороги с плитки в (" + x + "," + y + ")");

                    // Находим сегмент дороги ДО развилки
                    List<Tile> roadSegment = findCurrentRoadSegmentOnly(x, y);

                    // Фильтруем: оставляем только обычные дороги
                    List<Tile> roadsOnly = new ArrayList<>();
                    List<Tile> forksInSegment = new ArrayList<>();

                    for (Tile roadTile : roadSegment) {
                        if ("tile-l.png".equals(roadTile.getFileName())) {
                            forksInSegment.add(roadTile);
                            System.out.println("  Найден разделитель (развилка) в (" +
                                    roadTile.getX() + "," + roadTile.getY() + ")");
                        } else {
                            roadsOnly.add(roadTile);
                            visited.add(roadTile.getX() + "," + roadTile.getY());
                        }
                    }

                    if (!roadsOnly.isEmpty()) {
                        System.out.println("Дорожный сегмент из " + roadsOnly.size() + " плиток дорог");
                        System.out.println("Развилок в сегменте: " + forksInSegment.size());

                        // Проверяем, является ли это отдельным сегментом после развилки
                        boolean isSegmentAfterFork = false;
                        if (!forksInSegment.isEmpty()) {
                            // Проверяем, есть ли дороги ДО развилки
                            for (Tile fork : forksInSegment) {
                                int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                                for (int[] dir : directions) {
                                    int nx = fork.getX() + dir[0];
                                    int ny = fork.getY() + dir[1];

                                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                                        Tile neighbor = board[nx][ny];
                                        if (neighbor != null && neighbor.getType().equals("ROAD") &&
                                                !"tile-l.png".equals(neighbor.getFileName())) {
                                            // Проверяем соединение
                                            char forkSide = fork.getSide(getDirectionChar(dir));
                                            char neighborSide = neighbor.getSide(getOppositeDirectionChar(dir));

                                            if (forkSide == 'S' && neighborSide == 'S') {
                                                // Это соединение дороги с развилкой
                                                // Проверяем, есть ли в этом направлении другая дорога
                                                isSegmentAfterFork = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (isSegmentAfterFork) break;
                            }
                        }

                        // Подсчитываем владельцев миплов ТОЛЬКО на обычных дорогах
                        Map<String, Integer> ownerCount = new HashMap<>();
                        Map<String, List<Tile>> ownerTiles = new HashMap<>();

                        for (Tile roadTile : roadsOnly) {
                            System.out.println("  Плитка (" + roadTile.getX() + "," + roadTile.getY() +
                                    "), файл: " + roadTile.getFileName() +
                                    ", мипл: " + roadTile.hasMeeple() +
                                    ", владелец: " + (roadTile.hasMeeple() ? roadTile.getMeepleOwner() : "нет"));

                            if (roadTile.hasMeeple()) {
                                String owner = roadTile.getMeepleOwner();
                                ownerCount.put(owner, ownerCount.getOrDefault(owner, 0) + 1);

                                if (!ownerTiles.containsKey(owner)) {
                                    ownerTiles.put(owner, new ArrayList<>());
                                }
                                ownerTiles.get(owner).add(roadTile);
                            }
                        }

                        int totalPoints = roadsOnly.size(); // 1 очко за каждую плитку дороги
                        System.out.println("Всего очков за сегмент: " + totalPoints);

                        if (!ownerCount.isEmpty()) {
                            // Находим игрока с большинством миплов
                            String majorityOwner = null;
                            int maxCount = 0;
                            boolean tie = false;

                            for (Map.Entry<String, Integer> entry : ownerCount.entrySet()) {
                                System.out.println("  Игрок " + entry.getKey() + ": " + entry.getValue() + " миплов");
                                if (entry.getValue() > maxCount) {
                                    maxCount = entry.getValue();
                                    majorityOwner = entry.getKey();
                                    tie = false;
                                } else if (entry.getValue() == maxCount) {
                                    tie = true;
                                }
                            }

                            if (!tie && majorityOwner != null) {
                                // Один игрок владеет большинством миплов
                                Player player = getPlayerByColor(majorityOwner);
                                if (player != null) {
                                    int oldScore = player.getScore();
                                    player.addScore(totalPoints);

                                    String segmentType = isSegmentAfterFork ? "Сегмент дороги после развилки" : "Дорога";
                                    broadcastChat(segmentType + " (" + totalPoints + " плиток, через " +
                                            forksInSegment.size() + " развилок): " +
                                            player.getName() + " +" + totalPoints + " очков");

                                    System.out.println(player.getName() + " получает " + totalPoints +
                                            " очков (" + oldScore + " -> " + player.getScore() + ")");

                                    // Возвращаем миплы
                                    for (Tile meepleTile : ownerTiles.get(majorityOwner)) {
                                        meepleTile.setMeeple(false, null, null);
                                        player.returnMeeple();
                                    }
                                }
                            } else if (tie) {
                                // Ничья - делим очки поровну
                                int pointsEach = totalPoints / ownerCount.size();
                                int remainder = totalPoints % ownerCount.size();

                                System.out.println("Ничья! Делим " + totalPoints + " очков");

                                List<String> owners = new ArrayList<>(ownerCount.keySet());

                                // Распределяем очки поровну, остаток никому не даем
                                for (int i = 0; i < owners.size(); i++) {
                                    String owner = owners.get(i);
                                    Player player = getPlayerByColor(owner);
                                    if (player != null) {
                                        int pointsToAdd = pointsEach;
                                        player.addScore(pointsToAdd);

                                        String segmentType = isSegmentAfterFork ? "Сегмент дороги после развилки (разделен)" : "Дорога (разделена)";
                                        broadcastChat(segmentType + ": " + player.getName() +
                                                " +" + pointsToAdd + " очков");

                                        System.out.println(player.getName() + " получает " + pointsToAdd + " очков");

                                        // Возвращаем миплы
                                        List<Tile> tiles = ownerTiles.get(owner);
                                        if (tiles != null) {
                                            for (Tile meepleTile : tiles) {
                                                meepleTile.setMeeple(false, null, null);
                                                player.returnMeeple();
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Нет миплов на дороге
                            String segmentType = isSegmentAfterFork ? "Сегмент дороги после развилки" : "Дорога";
                            broadcastChat(segmentType + " (" + totalPoints + " плиток, через " +
                                    forksInSegment.size() + " развилок): нет миплов → 0 очков");
                            System.out.println("Нет миплов на дороге → 0 очков");
                        }

                        System.out.println("--- Конец сегмента ---");
                    }
                }
            }
        }

        // Проверяем, не остались ли дороги, начинающиеся от развилок
        System.out.println("\n=== ПРОВЕРКА ДОРОГ ОТ РАЗВИЛОК ===");
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                Tile tile = board[x][y];
                // Ищем развилки
                if (tile != null && "tile-l.png".equals(tile.getFileName())) {

                    // Проверяем все направления от развилки
                    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                    for (int[] dir : directions) {
                        int nx = x + dir[0];
                        int ny = y + dir[1];

                        if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                            Tile neighbor = board[nx][ny];
                            // Ищем дороги, соединенные с развилкой
                            if (neighbor != null &&
                                    neighbor.getType().equals("ROAD") &&
                                    !"tile-l.png".equals(neighbor.getFileName()) &&
                                    !visited.contains(nx + "," + ny)) {

                                // Проверяем соединение
                                char forkSide = tile.getSide(getDirectionChar(dir));
                                char neighborSide = neighbor.getSide(getOppositeDirectionChar(dir));

                                if (forkSide == 'S' && neighborSide == 'S') {
                                    System.out.println("Найдена дорога, начинающаяся от развилки в (" + nx + "," + ny + ")");

                                    // Находим сегмент этой дороги
                                    List<Tile> roadSegment = findCurrentRoadSegmentOnly(nx, ny);

                                    List<Tile> roadsOnly = new ArrayList<>();
                                    List<Tile> forksInSegment = new ArrayList<>();

                                    for (Tile roadTile : roadSegment) {
                                        if ("tile-l.png".equals(roadTile.getFileName())) {
                                            forksInSegment.add(roadTile);
                                        } else {
                                            roadsOnly.add(roadTile);
                                            visited.add(roadTile.getX() + "," + roadTile.getY());
                                        }
                                    }

                                    if (!roadsOnly.isEmpty()) {
                                        System.out.println("Сегмент от развилки: " + roadsOnly.size() + " плиток");

                                        // Подсчет очков для этого сегмента
                                        Map<String, Integer> ownerCount = new HashMap<>();
                                        Map<String, List<Tile>> ownerTiles = new HashMap<>();

                                        for (Tile roadTile : roadsOnly) {
                                            if (roadTile.hasMeeple()) {
                                                String owner = roadTile.getMeepleOwner();
                                                ownerCount.put(owner, ownerCount.getOrDefault(owner, 0) + 1);

                                                if (!ownerTiles.containsKey(owner)) {
                                                    ownerTiles.put(owner, new ArrayList<>());
                                                }
                                                ownerTiles.get(owner).add(roadTile);
                                            }
                                        }

                                        int totalPoints = roadsOnly.size();

                                        if (!ownerCount.isEmpty()) {
                                            String majorityOwner = null;
                                            int maxCount = 0;
                                            boolean tie = false;

                                            for (Map.Entry<String, Integer> entry : ownerCount.entrySet()) {
                                                if (entry.getValue() > maxCount) {
                                                    maxCount = entry.getValue();
                                                    majorityOwner = entry.getKey();
                                                    tie = false;
                                                } else if (entry.getValue() == maxCount) {
                                                    tie = true;
                                                }
                                            }

                                            if (!tie && majorityOwner != null) {
                                                Player player = getPlayerByColor(majorityOwner);
                                                if (player != null) {
                                                    player.addScore(totalPoints);
                                                    broadcastChat("Сегмент дороги от развилки (" + totalPoints +
                                                            " плиток): " + player.getName() + " +" + totalPoints + " очков");

                                                    // Возвращаем миплы
                                                    for (Tile meepleTile : ownerTiles.get(majorityOwner)) {
                                                        meepleTile.setMeeple(false, null, null);
                                                        player.returnMeeple();
                                                    }
                                                }
                                            } else if (tie) {
                                                int pointsEach = totalPoints / ownerCount.size();
                                                for (String owner : ownerCount.keySet()) {
                                                    Player player = getPlayerByColor(owner);
                                                    if (player != null) {
                                                        player.addScore(pointsEach);
                                                        broadcastChat("Сегмент дороги от развилки (разделен): " +
                                                                player.getName() + " +" + pointsEach + " очков");

                                                        // Возвращаем миплы
                                                        List<Tile> tiles = ownerTiles.get(owner);
                                                        if (tiles != null) {
                                                            for (Tile meepleTile : tiles) {
                                                                meepleTile.setMeeple(false, null, null);
                                                                player.returnMeeple();
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            broadcastChat("Сегмент дороги от развилки (" + totalPoints +
                                                    " плиток): нет миплов → 0 очков");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("=== ЗАВЕРШЕН ПОДСЧЕТ ОЧКОВ ЗА ДОРОГИ ===");
    }
    /**
     * Находит ТОЛЬКО текущий сегмент дороги (до развилок)
     */
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

            // Включаем только дороги и развилки
            boolean isRoadRelated = tile.getType().equals("ROAD") ||
                    "tile-l.png".equals(tile.getFileName());

            if (!isRoadRelated) continue;

            visited.add(key);
            segment.add(tile);

            // Если это развилка - ОСТАНАВЛИВАЕМСЯ, не идем дальше
            if ("tile-l.png".equals(tile.getFileName())) {
                continue;
            }

            // Ищем соседей-дорог
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15) {
                    Tile neighbor = board[nx][ny];
                    if (neighbor != null && !visited.contains(nx + "," + ny)) {

                        boolean neighborIsRoadRelated = neighbor.getType().equals("ROAD") ||
                                "tile-l.png".equals(neighbor.getFileName());

                        if (neighborIsRoadRelated) {
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
    private List<Tile> findCompleteRoadSegment(int startX, int startY) {
        return findConnectedRoadSegment(startX, startY); // Используем тот же метод
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
        System.out.println("Подсчет очков за города...");
        Set<String> visited = new HashSet<>();

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                Tile tile = board[x][y];
                if (tile != null && tile.getType().equals("CITY") && !visited.contains(x + "," + y)) {

                    List<Tile> city = findConnectedCity(x, y);
                    boolean isTileNCity = false;

                    // Проверяем, не завершен ли город tile-n.png
                    for (Tile cityTile : city) {
                        if ("tile-n.png".equals(cityTile.getFileName())) {
                            isTileNCity = true;
                            break;
                        }
                    }

                    // Пропускаем завершенные города tile-n.png
                    if (isTileNCity) {
                        boolean alreadyCompleted = false;
                        for (Tile cityTile : city) {
                            for (String completedKey : completedCities.keySet()) {
                                if (completedKey.contains(cityTile.getX() + "," + cityTile.getY())) {
                                    alreadyCompleted = true;
                                    break;
                                }
                            }
                            if (alreadyCompleted) break;
                        }
                        if (alreadyCompleted) {
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
                        }
                    }

                    // Незавершенный город: 1 очко за плитку, завершенный: 2 очка за плитку
                    boolean isCompleted = isCityCompleted(city);
                    int pointsPerTile = isCompleted ? 2 : 1;
                    int totalPoints = city.size() * pointsPerTile;

                    if (!ownerCount.isEmpty()) {
                        // Находим игрока с большинством миплов
                        String majorityOwner = null;
                        int maxCount = 0;
                        boolean tie = false;

                        for (Map.Entry<String, Integer> entry : ownerCount.entrySet()) {
                            if (entry.getValue() > maxCount) {
                                maxCount = entry.getValue();
                                majorityOwner = entry.getKey();
                                tie = false;
                            } else if (entry.getValue() == maxCount) {
                                tie = true;
                            }
                        }

                        String status = isCompleted ? "Завершенный город" : "Незавершенный город";

                        if (!tie && majorityOwner != null) {
                            Player player = getPlayerByColor(majorityOwner);
                            if (player != null) {
                                player.addScore(totalPoints);
                                broadcastChat(status + " (" + city.size() + " плиток): " +
                                        player.getName() + " +" + totalPoints + " очков");

                                // Возвращаем миплы для завершенных городов
                                if (isCompleted) {
                                    for (Tile cityTile : city) {
                                        if (cityTile.hasMeeple() &&
                                                cityTile.getMeepleOwner().equals(majorityOwner)) {
                                            cityTile.setMeeple(false, null, null);
                                            player.returnMeeple();
                                        }
                                    }
                                }
                            }
                        } else if (tie) {
                            // Ничья - делим очки поровну
                            int pointsEach = totalPoints / ownerCount.size();
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
                        broadcastChat(status + " (" + city.size() + " плиток): нет миплов → 0 очков");
                    }

                    // Отмечаем все плитки как посещенные
                    for (Tile cityTile : city) {
                        visited.add(cityTile.getX() + "," + cityTile.getY());
                    }
                }
            }
        }
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
                            return false; // Нет соседа - город не завершен
                        } else {
                            // Проверяем, является ли сосед частью этого же города
                            boolean neighborInCity = false;
                            for (Tile cityTile : city) {
                                if (cityTile.getX() == nx && cityTile.getY() == ny) {
                                    neighborInCity = true;
                                    break;
                                }
                            }

                            if (!neighborInCity) {
                                // Сосед есть, но не в этом городе
                                char oppositeSide = getOppositeSide(side);
                                if (neighbor.getSide(oppositeSide) != 'C') {
                                    return false;
                                }
                            }
                        }
                    } else {
                        return false; // Выход за границы поля
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

        // Определяем тип плитки правильно
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
            // ГОРОД tile-n.png
            message.append(" (часть города). ");

            // Проверяем, не соединен ли уже с другим tile-n.png
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
                // ========== ОБРАБОТКА ДОРОГ ==========
                message.append(" (дорога). ");

                // НОВАЯ ЛОГИКА: находим ТОЛЬКО текущий сегмент
                List<Tile> currentSegment = findCurrentRoadSegmentOnly(tile.getX(), tile.getY());

                // Проверяем ТОЛЬКО текущий сегмент
                boolean roadOccupiedByOther = false;
                String otherPlayerName = null;

                for (Tile roadTile : currentSegment) {
                    if ("tile-l.png".equals(roadTile.getFileName())) {
                        continue; // Пропускаем развилки
                    }

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

                        message.append("Вы можете поставить мипла на дорогу (M) ");
                        message.append("(").append(roadTiles).append(" плиток дороги");
                        if (forks > 0) {
                            message.append(", через ").append(forks).append(" развилок");
                        }
                        message.append(") или пропустить ход (S)");
                    }
                }

            } else if (tileType.equals("MONASTERY")) {
                // ========== ОБРАБОТКА МОНАСТЫРЯ ==========
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
                        message.append("Пропустите ход (S)");
                    } else {
                        message.append("Вы можете поставить мипла на монастырь (M) или пропустить ход (S)");
                    }
                }

            } else if (tileType.equals("CITY")) {
                // ========== ОБРАБОТКА ГОРОДА (кроме tile-n.png) ==========
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
                        message.append("Пропустите ход (S)");
                    } else if (existingCityOwner != null && !existingCityOwner.equals(player.getColor())) {
                        String otherPlayerName = getPlayerNameByColor(existingCityOwner);
                        message.append("Город уже занят игроком ").append(otherPlayerName).append("! ");
                        message.append("Пропустите ход (S)");
                    } else {
                        message.append("Вы можете поставить мипла на город (M) ");
                        message.append("(").append(cityTiles.size()).append(" плиток) или пропустить ход (S)");
                    }
                }

            } else {
                // НЕИЗВЕСТНЫЙ ТИП
                message.append(". Нельзя поставить мипла на эту плитку. Пропустите ход (S)");
            }

        } else {
            // НЕЛЬЗЯ СТАВИТЬ МИПЛА
            message.append(". Нельзя поставить мипла на эту плитку. Пропустите ход (S)");
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

    private int countTilesOnBoard() {
        int count = 0;
        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                if (board[row][col] != null) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getPlayerCount() {
        return players.size();
    }
}