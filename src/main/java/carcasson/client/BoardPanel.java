package carcasson.client;

import carcasson.controller.AppController;
import carcasson.common.Tile;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class BoardPanel extends JPanel {
    private AppController controller;
    private Image background;
    private Map<String, Tile> placedTiles = new HashMap<>(); // Ключ: "x,y"
    private final int TILE_SIZE = 80;
    private final int GRID_SIZE = 15;
    private final int CENTER_X = 7;
    private final int CENTER_Y = 7;

    public BoardPanel(AppController controller) {
        this.controller = controller;
        setPreferredSize(new Dimension(TILE_SIZE * GRID_SIZE, TILE_SIZE * GRID_SIZE));

        try {
            background = new ImageIcon(getClass().getResource("/background.jpg")).getImage();
        } catch (Exception e) {
            background = null;
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int gridX = e.getX() / TILE_SIZE;
                int gridY = e.getY() / TILE_SIZE;

                if (gridX >= 0 && gridX < GRID_SIZE && gridY >= 0 && gridY < GRID_SIZE) {
                    controller.sendTilePlaced(gridX, gridY);
                }
            }
        });
    }

    public void initialize() {
        placedTiles.clear();
        // Добавляем стартовую плитку при инициализации
        Tile startTile = new Tile(0, "/tiles/tile-s.png",
                "N=C S=N W=S E=S NS=0 NE=0 NW=0 WE=1 SE=0 SW=0");
        startTile.setX(CENTER_X);
        startTile.setY(CENTER_Y);
        placeTile(startTile);
        repaint();
    }


    private void drawTile(Graphics g, Tile tile) {
        int screenX = tile.getX() * TILE_SIZE;
        int screenY = tile.getY() * TILE_SIZE;

        System.out.println("Рисуем плитку " + tile.getId() +
                " в координатах (" + tile.getX() + "," + tile.getY() + ")" +
                " -> экранные (" + screenX + "," + screenY + ")" +
                " - " + tile.getFileName());
        try {
            String fileName = tile.getImagePath();
            if (fileName != null) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                System.out.println("Пытаемся загрузить: /tiles/" + fileName);

                ImageIcon icon = new ImageIcon(getClass().getResource("/tiles/" + fileName));
                System.out.println("Загрузка изображения: width=" + icon.getIconWidth() +
                        ", height=" + icon.getIconHeight());

                if (icon.getIconWidth() > 0) {
                    Image image = icon.getImage();

                    if (tile.getRotation() > 0) {
                        image = rotateImage(image, tile.getRotation() * 90);
                    }

                    g.drawImage(image, screenX, screenY, TILE_SIZE, TILE_SIZE, this);
                    System.out.println("Изображение успешно отрисовано");

                    if (tile.hasMeeple()) {
                        drawMeeple(g, tile, screenX, screenY);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("Ошибка при отрисовке: " + e.getMessage());
            e.printStackTrace();
        }

        // Fallback
        System.out.println("Используем fallback для плитки " + tile.getId());
        g.setColor(Color.ORANGE);
        g.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
        g.setColor(Color.BLACK);
        g.drawRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
        g.drawString("T" + tile.getId(), screenX + 10, screenY + 40);

        if (tile.hasMeeple()) {
            drawMeeple(g, tile, screenX, screenY);
        }
    }

    private void drawMeeple(Graphics g, Tile tile, int screenX, int screenY) {
        if (!tile.hasMeeple()) return;

        Color meepleColor = tile.getMeepleOwner().equals("RED") ?
                new Color(255, 50, 50, 200) :  // Красный с прозрачностью
                new Color(50, 100, 255, 200);   // Синий с прозрачностью

        g.setColor(meepleColor);

        // Рисуем мипла в центре плитки
        int meepleSize = TILE_SIZE / 2;
        int meepleX = screenX + (TILE_SIZE - meepleSize) / 2;
        int meepleY = screenY + (TILE_SIZE - meepleSize) / 2;

        // Рисуем фигурку мипла (как человечек)
        // Голова
        int headSize = meepleSize / 3;
        g.fillOval(meepleX + (meepleSize - headSize)/2,
                meepleY,
                headSize, headSize);

        // Тело (треугольник)
        int[] xPoints = {
                meepleX + meepleSize/2,
                meepleX + meepleSize/4,
                meepleX + 3*meepleSize/4
        };
        int[] yPoints = {
                meepleY + headSize,
                meepleY + meepleSize,
                meepleY + meepleSize
        };
        g.fillPolygon(xPoints, yPoints, 3);

        // Черная обводка
        g.setColor(Color.BLACK);
        g.drawOval(meepleX + (meepleSize - headSize)/2,
                meepleY,
                headSize, headSize);
        g.drawPolygon(xPoints, yPoints, 3);
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        System.out.println("=== paintComponent() вызван ===");
        System.out.println("Размер placedTiles: " + placedTiles.size());

        if (placedTiles.isEmpty()) {
            System.out.println("ВНИМАНИЕ: placedTiles пуст!");
        }

        if (background != null) {
            g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
        }

        g.setColor(new Color(150, 150, 150, 100));
        for (int i = 0; i <= GRID_SIZE; i++) {
            g.drawLine(i * TILE_SIZE, 0, i * TILE_SIZE, GRID_SIZE * TILE_SIZE);
            g.drawLine(0, i * TILE_SIZE, GRID_SIZE * TILE_SIZE, i * TILE_SIZE);
        }

        // Рисуем все плитки
        System.out.println("Рисуем " + placedTiles.size() + " плиток на поле");
        for (Tile tile : placedTiles.values()) {
            System.out.println("  Рисуем плитку в (" + tile.getX() + "," + tile.getY() +
                    ") - " + tile.getFileName());
            drawTile(g, tile);
        }

        // Отмечаем стартовую позицию
        g.setColor(Color.RED);
        g.drawRect(CENTER_X * TILE_SIZE, CENTER_Y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("Старт", CENTER_X * TILE_SIZE + 5, CENTER_Y * TILE_SIZE + 15);

        // Отображаем информацию о количестве плиток
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.drawString("Плиток: " + placedTiles.size(), 5, 15);
        System.out.println("paintComponent() завершен");
    }

    private Image rotateImage(Image image, double angle) {
        double rads = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(rads));
        double cos = Math.abs(Math.cos(rads));

        int width = image.getWidth(null);
        int height = image.getHeight(null);

        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();

        g2d.translate((newWidth - width) / 2, (newHeight - height) / 2);
        g2d.rotate(rads, width / 2.0, height / 2.0);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    public void updateBoard(Object gameState) {
        System.out.println("=== BoardPanel.updateBoard() вызван ===");
        System.out.println("Тип gameState: " + (gameState != null ? gameState.getClass().getName() : "null"));

        if (gameState instanceof Tile[][]) {
            Tile[][] board = (Tile[][]) gameState;
            System.out.println("Получен двумерный массив " + board.length + "x" +
                    (board.length > 0 ? board[0].length : 0));

            // Выводим всю структуру массива для отладки
            System.out.println("Содержимое board массива:");
            for (int i = 0; i < board.length; i++) {
                for (int j = 0; j < board[i].length; j++) {
                    if (board[i][j] != null) {
                        Tile t = board[i][j];
                        System.out.println("  board[" + i + "][" + j + "] -> Tile id=" + t.getId() +
                                ", x=" + t.getX() + ", y=" + t.getY() +
                                ", file=" + t.getFileName());
                    }
                }
            }

            placedTiles.clear();
            int count = 0;

            for (int i = 0; i < board.length; i++) {
                for (int j = 0; j < board[i].length; j++) {
                    Tile tile = board[i][j];
                    if (tile != null) {
                        count++;

                        // Используем координаты из плитки
                        int x = tile.getX();
                        int y = tile.getY();

                        System.out.println("  Обработка плитки " + count + ": board[" + i + "][" + j + "] -> " +
                                "координаты (" + x + "," + y +
                                "), файл: " + tile.getFileName());

                        // Проверяем валидность координат
                        if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) {
                            System.out.println("    Координаты невалидны, используем индексы массива");
                            x = i;
                            y = j;
                            tile.setX(x);
                            tile.setY(y);
                        }

                        String key = x + "," + y;

                        // Проверяем уникальность ключа
                        if (placedTiles.containsKey(key)) {
                            System.out.println("    ОШИБКА: Дублирование ключа " + key);
                            System.out.println("    Существующая плитка: " + placedTiles.get(key).getFileName());
                            System.out.println("    Новая плитка: " + tile.getFileName());
                        }

                        placedTiles.put(key, tile);
                        System.out.println("    Добавлена в placedTiles с ключом: " + key);
                    }
                }
            }

            System.out.println("Всего плиток добавлено в placedTiles: " + placedTiles.size());
            System.out.println("Ключи в placedTiles: " + placedTiles.keySet());

            // Проверяем, правильно ли отрисуются плитки
            System.out.println("Проверка отрисовки:");
            for (Map.Entry<String, Tile> entry : placedTiles.entrySet()) {
                Tile t = entry.getValue();
                int screenX = t.getX() * TILE_SIZE;
                int screenY = t.getY() * TILE_SIZE;
                System.out.println("  Ключ: " + entry.getKey() +
                        " -> экранные координаты: (" + screenX + "," + screenY + ")");
            }

            // Принудительно вызываем перерисовку
            repaint();
            System.out.println("Вызван repaint()");

        } else {
            System.out.println("ОШИБКА: gameState не является Tile[][]");
            if (gameState != null) {
                System.out.println("Фактический тип: " + gameState.getClass().getName());
                if (gameState instanceof Object[]) {
                    System.out.println("Это массив объектов длины: " + ((Object[])gameState).length);
                }
            }
        }
    }

    // Вспомогательный метод для размещения плитки
    public void placeTile(Tile tile) {
        String key = tile.getX() + "," + tile.getY();
        placedTiles.put(key, tile);
        repaint();
    }
}