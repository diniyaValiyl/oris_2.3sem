package carcasson.client;

import carcasson.controller.AppController;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ControlPanel extends JPanel {
    private AppController controller;
    private JButton placeMeepleButton;
    private JButton skipButton;
    private JButton endGameButton;

    public ControlPanel(AppController controller) {
        this.controller = controller;
        setLayout(new GridLayout(5, 1, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Управление"));
        setPreferredSize(new Dimension(350, 300)); // Увеличил высоту

        placeMeepleButton = new JButton("Поставить мипла ");
        placeMeepleButton.addActionListener(e -> placeMeeple());
        placeMeepleButton.setToolTipText("Поставить мипла на последнюю размещенную плитку");

        skipButton = new JButton("Пропустить мипла ");
        skipButton.addActionListener(e -> controller.sendTurnSkip());
        skipButton.setToolTipText("Не ставить мипла на этот ход");

        endGameButton = new JButton("Завершить игру");
        endGameButton.addActionListener(e -> endGame());


        JLabel rulesLabel = new JLabel(
                "<html><center><b>НОВЫЕ ПРАВИЛА:</b><br>" +
                        "• <font color='blue'>РАЗВИЛКА</font>: на ней не может быть мипла <br>" +
                        "• <font color='red'>ГОРОД</font>: расчитывается с бонусами<br>" +
                        "• <font color='green'>МОНАСТЫРЬ</font>: 1+соседи очков<br>" +
                        "• ВСЕГО МИПЛОВ 4 У КАЖДОГО</center></html>"
        );
        rulesLabel.setHorizontalAlignment(SwingConstants.CENTER);

        add(placeMeepleButton);
        add(skipButton);
        add(endGameButton);
        add(rulesLabel);

        // Горячие клавиши надо допилить
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "placeMeeple");
        getActionMap().put("placeMeeple", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (placeMeepleButton.isEnabled()) {
                    placeMeeple();
                }
            }
        });
    }

    private void placeMeeple() {
        controller.sendMeeplePlaced();
    }

    private void endGame() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Завершить игру досрочно?\n" +
                        "Победитель определится по текущим очкам.",
                "Завершение игры",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            controller.sendEndGame();
        }
    }

    public void setEnabled(boolean enabled) {
        placeMeepleButton.setEnabled(enabled);
        skipButton.setEnabled(enabled);
        endGameButton.setEnabled(true);
    }
}