package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
import client.nowhere.model.GameBoard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GameBoardHelper {
    
    private final GameSessionDAO gameSessionDAO;
    
    @Autowired
    public GameBoardHelper(GameSessionDAO gameSessionDAO) {
        this.gameSessionDAO = gameSessionDAO;
    }
    
    public GameBoard getGameBoard(String gameCode) {
        return gameSessionDAO.getGame(gameCode).getGameBoard();
    }
}

