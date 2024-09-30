package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
import client.nowhere.model.GameSession;
import client.nowhere.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class GameSessionHelper {

    private final GameSessionDAO gameSessionDAO;

    @Autowired
    public GameSessionHelper(GameSessionDAO gameSessionDAO) {
        this.gameSessionDAO = gameSessionDAO;
    }

    public GameSession createGameSession() {
        return gameSessionDAO.createGameSession(generateSessionCode());
    }

    public GameSession updateGameSession(GameSession gameSession) {
        return gameSessionDAO.updateGameSession(gameSession);
    }

    private String generateSessionCode() {
        String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder stringBuilder = new StringBuilder();
        Random rnd = new Random();
        while (stringBuilder.length() < 5) {
            int index = (int) (rnd.nextFloat() * CHARS.length());
            stringBuilder.append(CHARS.charAt(index));
        }
        String sessionCode = stringBuilder.toString();
        return sessionCode;
    }

    public Player joinPlayer(Player player) {
        return this.gameSessionDAO.joinGameSession(player);
    }
}
