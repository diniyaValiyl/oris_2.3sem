package carcasson.common;

import java.io.Serializable;

public class GameMessage implements Serializable {
    private String type;
    private String sender;
    private Object data;

    public GameMessage(String type, String sender, Object data) {
        this.type = type;
        this.sender = sender;
        this.data = data;
    }

    public String getType() { return type; }
    public String getSender() { return sender; }
    public Object getData() { return data; }
}