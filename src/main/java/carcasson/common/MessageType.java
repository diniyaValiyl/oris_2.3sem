package carcasson.common;

public enum MessageType {
    // Системные сообщения
    CONNECT,          // Подключение игрока
    DISCONNECT,       // Отключение игрока
    JOIN_GAME,        // Присоединение к игре
    GAME_START,       // Начало игры
    GAME_END,         // Конец игры

    // Игровые сообщения
    TILE_DRAWN,       // Игрок получил плитку
    TILE_PLACED,      // Плитка размещена на поле
    TILE_ROTATED,     // Плитка повернута
    MEEPLE_PLACED,    // Мипл размещен
    TURN_SKIP,        // Ход пропущен
    SCORE_UPDATE,     // Обновление счета

    // Чат
    CHAT_MESSAGE,     // Сообщение в чат

    // Состояние игры
    GAME_STATE,       // Полное состояние игры
    PLAYER_TURN,      // Чей ход
    TILE_UPDATE       // Обновление текущей плитки
}