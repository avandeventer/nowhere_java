package client.nowhere.helper;

import client.nowhere.dao.ActiveSessionDAO;
import client.nowhere.model.ActivePlayerSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActiveSessionHelper {

    private final ActiveSessionDAO activeSessionDAO;

    @Autowired
    public ActiveSessionHelper(ActiveSessionDAO activeSessionDAO) {
        this.activeSessionDAO = activeSessionDAO;
    }

    public ActivePlayerSession update(ActivePlayerSession activeSession) {
        return this.activeSessionDAO.update(activeSession);
    }
}
