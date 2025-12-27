package carcasson.common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public class Tile implements Serializable {
    private int id;
    private String imagePath;
    private String features;
    private int rotation;
    private int x;
    private int y;
    private boolean hasMeeple;
    private String meepleOwner;
    private String meepleType;

    private Set<String> occupiedFeatures = new HashSet<>();

    public Tile(int id, String imagePath, String features) {
        this.id = id;
        this.imagePath = imagePath;
        this.features = features;
        this.rotation = 0;
        this.x = -1;
        this.y = -1;
        this.hasMeeple = false;
        this.meepleType = null;
    }

    public int getId() { return id; }
    public String getImagePath() { return imagePath; }
    public String getFeatures() { return features; }
    public int getRotation() { return rotation; }

    public void setRotation(int rotation) {
        this.rotation = rotation % 4;
        if (this.rotation < 0) this.rotation += 4;
    }

    public void rotate() {
        rotation = (rotation + 1) % 4;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public boolean hasMeeple() { return hasMeeple; }

    public void setMeeple(boolean hasMeeple, String owner, String type) {
        this.hasMeeple = hasMeeple;
        this.meepleOwner = owner;
        this.meepleType = type;
        if (hasMeeple && type != null) {
            occupyFeature(type, owner);
        }
    }

    public String getMeepleOwner() { return meepleOwner; }
    public String getMeepleType() { return meepleType; }

    public char getSide(char side) {
        // Разбираем features
        char north = 'N';
        char east = 'N';
        char south = 'N';
        char west = 'N';

        String[] parts = features.split(" ");
        for (String part : parts) {
            if (part.startsWith("N=")) north = part.charAt(2);
            else if (part.startsWith("E=")) east = part.charAt(2);
            else if (part.startsWith("S=")) south = part.charAt(2);
            else if (part.startsWith("W=")) west = part.charAt(2);
        }

        // Массив сторон в порядке: N, E, S, W
        char[] sides = {north, east, south, west};

        // Определяем исходный индекс стороны
        int originalIndex = -1;
        switch(side) {
            case 'N': originalIndex = 0; break;
            case 'E': originalIndex = 1; break;
            case 'S': originalIndex = 2; break;
            case 'W': originalIndex = 3; break;
        }

        if (originalIndex == -1) return 'N';

        // Учитываем вращение: при вращении по часовной стрелке стороны смещаются
        int rotatedIndex = (originalIndex - rotation + 4) % 4;
        if (rotatedIndex < 0) rotatedIndex += 4;

        return sides[rotatedIndex];
    }

    public String getType() {
        // Проверяем сначала монастырь
        if (features.contains("CL=1")) {
            return "MONASTERY";
        }

        // Проверяем город
        if (features.contains("C=")) {
            return "CITY";
        }

        // Проверяем дорогу
        if (features.contains("S=") ||
                features.contains("NS=1") || features.contains("WE=1") ||
                features.contains("NE=1") || features.contains("NW=1") ||
                features.contains("SE=1") || features.contains("SW=1")) {
            return "ROAD";
        }

        return "FIELD"; // Поле
    }

    // Метод для проверки, является ли плитка tile-n.png
    public boolean isTileN() {
        return "tile-n.png".equals(getFileName());
    }

    // Метод для проверки, является ли плитка tile-l.png
    public boolean isTileL() {
        return "tile-l.png".equals(getFileName());
    }

    public boolean canPlaceMeeple() {
        String type = getType();

        // Проверяем специальные случаи плиток, на которые нельзя ставить мипла
        String fileName = getFileName();
        if (fileName != null) {
            if (fileName.equals("tile-l.png") || fileName.equals("tile-s.png")) {
                return false; // На эти плитки нельзя ставить мипла
            }
        }

        // Можно ставить мипла только на город, дорогу или монастырь
        return type.equals("CITY") || type.equals("ROAD") || type.equals("MONASTERY");
    }

    public static boolean areSidesCompatible(char side1, char side2) {
        // Город должен соединяться с городом
        if (side1 == 'C' && side2 == 'C') return true;
        // Дорога должна соединяться с дорогой
        if (side1 == 'S' && side2 == 'S') return true;
        // Поле должно соединяться с полем
        if (side1 == 'N' && side2 == 'N') return true;
        return false;
    }

    // Дополнительный метод для проверки совместимости с развилкой
    public static boolean areSidesCompatibleWithFork(char side1, char side2) {
        // Для развилки: дорога может соединяться с дорогой или полем
        // Поле может соединяться только с полем
        if (side1 == 'S' && side2 == 'S') return true; // дорога-дорога
        if (side1 == 'S' && side2 == 'N') return true; // дорога-поле (для развилки)
        if (side1 == 'N' && side2 == 'S') return true; // поле-дорога (для развилки)
        if (side1 == 'N' && side2 == 'N') return true; // поле-поле
        return false;
    }

    public String getFileName() {
        return imagePath.substring(imagePath.lastIndexOf("/") + 1);
    }

    // Метод для проверки, занят ли объект
    public boolean isFeatureOccupied(String featureType) {
        return occupiedFeatures.contains(featureType);
    }

    // Метод для занятия объекта
    public void occupyFeature(String featureType, String playerColor) {
        occupiedFeatures.add(featureType);
    }

    // Метод для освобождения объекта
    public void freeFeature(String featureType) {
        occupiedFeatures.remove(featureType);
    }

    // Получить все занятые объекты
    public Set<String> getOccupiedFeatures() {
        return new HashSet<>(occupiedFeatures);
    }

    // ============ МЕТОДЫ ДЛЯ TILE-N.PNG ============

    public boolean isCityTileN() {
        String fileName = getFileName();
        return fileName != null && fileName.equals("tile-n.png") && getSide('N') == 'C';
    }

    public char getCitySide() {
        if (!isCityTileN()) return 'N';

        // У tile-n.png город всегда на северной стороне в features
        // Учитываем вращение
        char[] sides = {'N', 'W', 'S', 'E'};
        return sides[rotation % 4];
    }

    // ============ МЕТОДЫ ДЛЯ TILE-L.PNG ============

    // Исправленный метод: развилка имеет 3 дороги и 1 поле
    public boolean isRoadFork() {
        String fileName = getFileName();
        if (fileName == null || !fileName.equals("tile-l.png")) return false;

        // Подсчитываем количество дорожных сторон
        int roadCount = 0;
        char[] sidesToCheck = {'N', 'E', 'S', 'W'};
        for (char side : sidesToCheck) {
            if (getSide(side) == 'S') roadCount++;
        }

        return roadCount == 3; // Развилка имеет 3 дороги и 1 поле
    }

    // Метод для получения всех дорожных сторон развилки
    public ArrayList<Character> getRoadSides() {
        ArrayList<Character> roadSides = new ArrayList<>();

        if (isRoadFork()) {
            char[] sides = {'N', 'E', 'S', 'W'};
            for (char side : sides) {
                if (getSide(side) == 'S') {
                    roadSides.add(side);
                }
            }
        }
        return roadSides;
    }

    // Метод для получения стороны поля у развилки
    public char getFieldSide() {
        if (!isRoadFork()) return 'N';

        char[] sides = {'N', 'E', 'S', 'W'};
        for (char side : sides) {
            if (getSide(side) == 'N') {
                return side;
            }
        }
        return 'N';
    }

    // Метод для проверки, является ли сторона дорожной
    public boolean isRoadSide(char side) {
        return getSide(side) == 'S';
    }

    // Метод для проверки, является ли сторона полем
    public boolean isFieldSide(char side) {
        return getSide(side) == 'N';
    }

    @Override
    public String toString() {
        return "Tile{id=" + id + ", file='" + getFileName() + "', rotation=" + rotation +
                ", type=" + getType() + ", x=" + x + ", y=" + y +
                ", N=" + getSide('N') + ", E=" + getSide('E') +
                ", S=" + getSide('S') + ", W=" + getSide('W') + "}";
    }
}