package carcasson.client;

import carcasson.controller.AppController;
import carcasson.common.Player;
import javax.swing.*;
import java.awt.*;

public class PlayerPanel extends JPanel {
    private AppController controller;
    private JLabel nameLabel;
    private JLabel scoreLabel;
    private JLabel meeplesLabel;
    private JLabel turnLabel;

    public PlayerPanel(AppController controller) {
        this.controller = controller;
        setLayout(new GridLayout(4, 1, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Информация об игроке"));
        setPreferredSize(new Dimension(300, 120));

        nameLabel = new JLabel("Имя: ");
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));

        scoreLabel = new JLabel("Очки: 0");
        scoreLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        meeplesLabel = new JLabel("Миплов: 4/4");
        meeplesLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        turnLabel = new JLabel("Ожидание начала игры...");
        turnLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        turnLabel.setForeground(Color.BLUE);

        add(nameLabel);
        add(scoreLabel);
        add(meeplesLabel);
        add(turnLabel);
    }

    public void setPlayerName(String name) {
        nameLabel.setText("Имя: " + name);
    }

    public void setPlayerInfo(Player player) {
        nameLabel.setText("Имя: " + player.getName());
        scoreLabel.setText("Очки: " + player.getScore());
        meeplesLabel.setText("Миплов: " + player.getMeeplesLeft() + "/4");

        Color playerColor = player.getColor().equals("RED") ? Color.RED : Color.BLUE;
        nameLabel.setForeground(playerColor);
    }

    public void updateScore(int score, int meeplesLeft) {
        scoreLabel.setText("Очки: " + score);
        meeplesLabel.setText("Миплов: " + meeplesLeft + "/4");
    }

    public void setCurrentTurn(String playerName) {
        if (playerName.equals(controller.getUsername())) {
            turnLabel.setText("Сейчас ваш ход!");
            turnLabel.setForeground(Color.GREEN);
        } else {
            turnLabel.setText("Ходит: " + playerName);
            turnLabel.setForeground(Color.RED);
        }
    }
}