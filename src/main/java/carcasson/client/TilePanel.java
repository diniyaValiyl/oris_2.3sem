package carcasson.client;

import carcasson.controller.AppController;
import carcasson.common.Tile;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class TilePanel extends JPanel {
    private AppController controller;
    private Tile currentTile;
    private JLabel tileLabel;
    private JButton rotateButton;
    private boolean enabled;

    public TilePanel(AppController controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Текущая плитка"));
        setPreferredSize(new Dimension(350, 200));

        tileLabel = new JLabel("Ожидание плитки...", SwingConstants.CENTER);
        tileLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        rotateButton = new JButton("Повернуть плитку ");
        rotateButton.setEnabled(false);
        rotateButton.addActionListener(e -> rotateTile());

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotate");
        getActionMap().put("rotate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (enabled && currentTile != null) {
                    rotateTile();
                }
            }
        });

        add(tileLabel, BorderLayout.CENTER);
        add(rotateButton, BorderLayout.SOUTH);
    }

    public void setTile(Tile tile) {
        this.currentTile = tile;
        updateTileDisplay();
    }

    private void updateTileDisplay() {
        if (currentTile == null) {
            tileLabel.setText("Ожидание плитки...");
            tileLabel.setIcon(null);
        } else {
            try {
                String fileName;
                if (enabled) {
                    // Показываем реальную плитку с учетом вращения
                    fileName = currentTile.getImagePath();
                    fileName = fileName.substring(fileName.lastIndexOf("/") + 1);

                    ImageIcon icon = new ImageIcon(getClass().getResource("/tiles/" + fileName));
                    Image image = icon.getImage();

                    if (currentTile.getRotation() > 0) {
                        image = rotateImage(image, currentTile.getRotation() * 90);
                    }

                    Image scaledImage = image.getScaledInstance(120, 120, Image.SCALE_SMOOTH);
                    tileLabel.setIcon(new ImageIcon(scaledImage));
                } else {
                    // Показываем рубашку
                    fileName = "tile_back.png";
                    ImageIcon icon = new ImageIcon(getClass().getResource("/tiles/" + fileName));
                    Image scaledImage = icon.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH);
                    tileLabel.setIcon(new ImageIcon(scaledImage));
                }

                tileLabel.setText(null);

            } catch (Exception e) {
                tileLabel.setIcon(null);
                tileLabel.setText("Плитка #" + currentTile.getId());
            }
        }
        rotateButton.setEnabled(enabled && currentTile != null);
    }

    private Image rotateImage(Image image, double angle) {
        BufferedImage buffered = new BufferedImage(
                image.getWidth(null),
                image.getHeight(null),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2d = buffered.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        // Поворачиваем
        BufferedImage rotated = new BufferedImage(
                buffered.getHeight(),
                buffered.getWidth(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2dRotated = rotated.createGraphics();
        g2dRotated.rotate(Math.toRadians(angle),
                rotated.getWidth() / 2.0,
                rotated.getHeight() / 2.0);
        g2dRotated.drawImage(buffered,
                (rotated.getWidth() - buffered.getWidth()) / 2,
                (rotated.getHeight() - buffered.getHeight()) / 2,
                null);
        g2dRotated.dispose();

        return rotated;
    }

    private void rotateTile() {
        if (currentTile != null) {
            currentTile.rotate();
            updateTileDisplay();
            controller.sendTileRotated();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        rotateButton.setEnabled(enabled && currentTile != null);
        updateTileDisplay();
    }
}