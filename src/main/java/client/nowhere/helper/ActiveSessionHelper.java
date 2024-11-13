package client.nowhere.helper;

import client.nowhere.dao.ActiveSessionDAO;
import client.nowhere.model.ActiveGameStateSession;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActiveSessionHelper {

    private final ActiveSessionDAO activeSessionDAO;
    private final GameSessionHelper gameSessionHelper;

    @Autowired
    public ActiveSessionHelper(ActiveSessionDAO activeSessionDAO, GameSessionHelper gameSessionHelper) {
        this.activeSessionDAO = activeSessionDAO;
        this.gameSessionHelper = gameSessionHelper;
    }

    public ActivePlayerSession update(ActivePlayerSession activeSession) {
        return this.activeSessionDAO.update(activeSession);
    }

    public ActiveGameStateSession update(String gameCode, String authorId, boolean isDone) {
        ActiveGameStateSession activeGameStateSession = this.activeSessionDAO.update(gameCode, authorId, isDone);
        if(!activeGameStateSession.getIsPlayerDone().containsValue(false)) {
            GameSession gameSession = gameSessionHelper.getGame(gameCode);
            gameSession.setGameStateToNext();
            gameSessionHelper.updateGameSession(gameSession, false);
        }
        return activeGameStateSession;
    }
}
