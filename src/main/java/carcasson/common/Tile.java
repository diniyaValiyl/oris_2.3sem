package carcasson.common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

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

    // Добавим: какие объекты на плитке уже заняты
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

    // ВАЖНО: Исправленный метод getSide с правильной логикой вращения
    public char getSide(char side) {
        // Разбираем features
        char north = 'N'; // по умолчанию
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

        // Учитываем вращение: поворачиваем массив
        int index = -1;
        switch(side) {
            case 'N': index = 0; break;
            case 'E': index = 1; break;
            case 'S': index = 2; break;
            case 'W': index = 3; break;
        }

        // Корректируем индекс с учетом вращения
        // При вращении по часовой стрелке: N→E→S→W
        int adjustedIndex = (index - rotation + 4) % 4;

        return sides[adjustedIndex];
    }

    public String getType() {
        // Определяем тип плитки по features
        if (features.contains("CL=1")) {
            return "MONASTERY";
        }

        // Если есть C= - это город
        if (features.contains("C=")) {
            return "CITY";
        }

        // Если есть S= - это дорога
        if (features.contains("S=")) {
            // Проверяем, есть ли хоть одна сторона с дорогой
            String[] parts = features.split(" ");
            for (String part : parts) {
                if (part.startsWith("N=") && part.charAt(2) == 'S') return "ROAD";
                if (part.startsWith("E=") && part.charAt(2) == 'S') return "ROAD";
                if (part.startsWith("S=") && part.charAt(2) == 'S') return "ROAD";
                if (part.startsWith("W=") && part.charAt(2) == 'S') return "ROAD";
            }
        }

        return "FIELD"; // Поле
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

    // Метод для освобождения объекта (если понадобится)
    public void freeFeature(String featureType) {
        occupiedFeatures.remove(featureType);
    }

    // Получить все занятые объекты
    public Set<String> getOccupiedFeatures() {
        return new HashSet<>(occupiedFeatures);
    }

    @Override
    public String toString() {
        return "Tile{id=" + id + ", file='" + getFileName() + "', rotation=" + rotation + "}";
    }
}