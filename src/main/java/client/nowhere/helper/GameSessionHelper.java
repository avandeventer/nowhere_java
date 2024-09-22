package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
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

    public String createGameSession() {
        return gameSessionDAO.createGameSession(generateSessionCode());
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

    public Player joinPlayer(String gameCode, String playerFirstName, String playerLastName) {
        return this.gameSessionDAO.joinGameSession(gameCode, playerFirstName, playerLastName);
    }
}
