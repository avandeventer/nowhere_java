package client.nowhere.helper;

import client.nowhere.dao.ActiveSessionDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        if (activeSession.isSetNextPlayerTurn() && !activeSession.getPlayerId().isEmpty()) {
            //setting isDone to false here ensures it's not updated
            this.update(activeSession.getGameCode(), activeSession.getPlayerId(), false, true);
        }
        return this.activeSessionDAO.update(activeSession);
    }

    public ActiveGameStateSession update(String gameCode, String authorId, boolean isDone, boolean isDoneWithTurn) {
        return this.activeSessionDAO.update(gameCode, authorId, isDone, isDoneWithTurn);
    }

    public ActivePlayerSession nextPlayerTurn(String gameCode) {
        GameSession gameSession = this.gameSessionHelper.getGame(gameCode);
        ActivePlayerSession activePlayerSession = gameSession.getActivePlayerSession();
        activePlayerSession.setGameCode(gameCode);
        ActiveGameStateSession activeGameStateSession = gameSession.getActiveGameStateSession();

        String currentTurnPlayerId = activePlayerSession.getPlayerId();

        if (currentTurnPlayerId.isEmpty()) {
            currentTurnPlayerId = gameSession.getPlayers().stream()
                    .min(Comparator.comparing(Player::getJoinedAt)).get().getAuthorId();
            activePlayerSession.setPlayerId(currentTurnPlayerId);
            activePlayerSession.setGameCode(gameCode);
            activePlayerSession.setSetNextPlayerTurn(false);
            activeSessionDAO.update(activePlayerSession);
        }

        if (!activeGameStateSession.getIsPlayerDoneWithTurn().get(currentTurnPlayerId)) {
            return activePlayerSession;
        }

        if (activeGameStateSession.getIsPlayerDoneWithTurn().values().stream().allMatch(doneWithTurn -> doneWithTurn)) {
            activeGameStateSession.resetPlayerDoneWithTurn(gameSession.getPlayers());
        }

        List<Player> playersInCurrentTurnOrder = getPlayersInCurrentTurnOrder(gameSession, currentTurnPlayerId);

        for (Player nextPlayer : playersInCurrentTurnOrder) {
            String nextPlayerId = nextPlayer.getAuthorId();

            boolean turnDone = activeGameStateSession.getIsPlayerDoneWithTurn().get(nextPlayerId);
            boolean roundDone = activeGameStateSession.getIsPlayerDone().get(nextPlayerId);

            if (!turnDone && !roundDone) {
                activePlayerSession.resetActivePlayerSession();
                activePlayerSession.setPlayerId(nextPlayerId);
                activePlayerSession.setSetNextPlayerTurn(false);
                this.activeSessionDAO.update(activePlayerSession);
                break;
            }
        }

        return activePlayerSession;
    }

    private List<Player> getPlayersInCurrentTurnOrder(GameSession gameSession, String currentTurnPlayerId) {
        List<Player> playersInTurnOrder = new ArrayList<>(gameSession.getPlayers());
        playersInTurnOrder.sort(Comparator.comparing(Player::getJoinedAt));

        int currentIndex = getCurrentPlayerIndex(currentTurnPlayerId, playersInTurnOrder);
        int totalPlayers = playersInTurnOrder.size();

        List<Player> rotatedList = new ArrayList<>();
        for (int i = 1; i < totalPlayers; i++) {
            //Modulus sets nextIndex to the person next in the turn order instead of the literal index
            int nextIndex = (currentIndex + i) % totalPlayers;
            rotatedList.add(playersInTurnOrder.get(nextIndex));
        }
        return rotatedList;
    }

    private int getCurrentPlayerIndex(String currentTurnPlayerId, List<Player> playersInTurnOrder) {
        int currentIndex = -1;
            for (int i = 0; i < playersInTurnOrder.size(); i++) {
                if (playersInTurnOrder.get(i).getAuthorId().equals(currentTurnPlayerId)) {
                    currentIndex = i;
                    break;
                }
            }

        if (currentIndex == -1) {
            throw new IllegalStateException("Current player not found in turn order.");
        }

        return currentIndex;
    }
}
