package client.nowhere.helper;

import client.nowhere.dao.EndingDAO;
import client.nowhere.model.Ending;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EndingHelper {

    EndingDAO endingDAO;

    @Autowired
    public EndingHelper (EndingDAO endingDAO) {
        this.endingDAO = endingDAO;
    }

    public Ending getAuthorEnding(String gameCode, String authorId) {
        return endingDAO.getAuthorEnding(gameCode, authorId);
    }

    public Ending getPlayerEnding(String gameCode, String playerId) {
        return endingDAO.getPlayerEnding(gameCode, playerId);
    }
}
