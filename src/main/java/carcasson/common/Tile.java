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

        // Учитываем вращение
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

        return "FIELD";
    }

    public boolean isTileN() {
        return "tile-n.png".equals(getFileName());
    }

    public boolean isTileL() {
        return "tile-l.png".equals(getFileName());
    }

    public boolean canPlaceMeeple() {
        String type = getType();

        String fileName = getFileName();
        if (fileName != null) {
            if (fileName.equals("tile-l.png") || fileName.equals("tile-s.png")) {
                return false;
            }
        }

        return type.equals("CITY") || type.equals("ROAD") || type.equals("MONASTERY");
    }



    public String getFileName() {
        return imagePath.substring(imagePath.lastIndexOf("/") + 1);
    }



    public void occupyFeature(String featureType, String playerColor) {
        occupiedFeatures.add(featureType);
    }


    @Override
    public String toString() {
        return "Tile{id=" + id + ", file='" + getFileName() + "', rotation=" + rotation +
                ", type=" + getType() + ", x=" + x + ", y=" + y +
                ", N=" + getSide('N') + ", E=" + getSide('E') +
                ", S=" + getSide('S') + ", W=" + getSide('W') + "}";
    }
}