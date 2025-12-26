package carcasson.common;

import java.io.Serializable;

public class Meeple implements Serializable {
    private String id;
    private String playerId;
    private String type;
    private int tileX;
    private int tileY;
    private int position;

    public Meeple(String id, String playerId, String type, int tileX, int tileY, int position) {
        this.id = id;
        this.playerId = playerId;
        this.type = type;
        this.tileX = tileX;
        this.tileY = tileY;
        this.position = position;
    }

    public String getId() { return id; }
    public String getPlayerId() { return playerId; }
    public String getType() { return type; }
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    public int getPosition() { return position; }
}