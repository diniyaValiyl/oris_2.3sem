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
        setPreferredSize(new Dimension(350, 250));

        // Кнопка "Поставить мипла" - теперь только одна
        placeMeepleButton = new JButton("Поставить мипла (M)");
        placeMeepleButton.addActionListener(e -> placeMeeple());
        placeMeepleButton.setToolTipText("Поставить мипла на последнюю размещенную плитку");

        // Кнопка "Пропустить"
        skipButton = new JButton("Пропустить мипла (S)");
        skipButton.addActionListener(e -> controller.sendTurnSkip());
        skipButton.setToolTipText("Не ставить мипла на этот ход");

        // Кнопка "Завершить игру"
        endGameButton = new JButton("Завершить игру");
        endGameButton.addActionListener(e -> endGame());

        // Информация о правилах
        JLabel rulesLabel = new JLabel(
                "<html><center><b>Правила подсчета очков:</b><br>" +
                        "• Город: 2 очка за каждую плитку города<br>" +
                        "• Дорога: 1 очко за каждую плитку дороги<br>" +
                        "• Монастырь: 1 очко + по 1 за соседа</center></html>"
        );
        rulesLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Подсказка по горячим клавишам
        JLabel helpLabel = new JLabel(
                "<html><center><b>Горячие клавиши:</b><br>" +
                        "R - повернуть плитку<br>" +
                        "M - поставить мипла<br>" +
                        "S - пропустить мипла<br>" +
                        "Клик на поле - разместить плитку</center></html>"
        );
        helpLabel.setHorizontalAlignment(SwingConstants.CENTER);

        add(placeMeepleButton);
        add(skipButton);
        add(endGameButton);
        add(rulesLabel);
        add(helpLabel);

        // Горячие клавиши
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