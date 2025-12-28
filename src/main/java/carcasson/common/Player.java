package carcasson.common;

import java.io.Serializable;
import java.awt.Color;

public class Player implements Serializable {
    private String id;
    private String name;
    private String color;
    private int score;
    private int meeplesLeft;

    public Player(String id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.score = 0;
        this.meeplesLeft = 4;
    }

    public String getName() { return name; }
    public String getColor() { return color; }
    public int getScore() { return score; }
    public void addScore(int points) { this.score += points; }

    public int getMeeplesLeft() { return meeplesLeft; }
    public void useMeeple() {
        if (meeplesLeft > 0) meeplesLeft--;
    }
    public void returnMeeple() { meeplesLeft++; }


    @Override
    public String toString() {
        return name + " (" + color + "): " + score + " очков, миплов: " + meeplesLeft;
    }
}