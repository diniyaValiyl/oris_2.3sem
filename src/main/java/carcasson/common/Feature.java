package carcasson.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Feature implements Serializable {
    private String type;
    private List<TilePosition> tiles = new ArrayList<>();
    private String ownerId;
    private boolean completed;

    public Feature(String type) {
        this.type = type;
        this.completed = false;
    }

    public String getType() { return type; }
    public List<TilePosition> getTiles() { return tiles; }
    public void addTile(TilePosition tile) { tiles.add(tile); }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public static class TilePosition implements Serializable {
        public int x;
        public int y;
        public int side;

        public TilePosition(int x, int y, int side) {
            this.x = x;
            this.y = y;
            this.side = side;
        }
    }
}