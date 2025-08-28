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
            activeSession = nextPlayerTurn(activeSession.getGameCode(), activeSession.getPlayerId());
        }
        return this.activeSessionDAO.update(activeSession);
    }

    public void update(String gameCode, GameState gamePhase, String authorId, boolean isDone) {
        boolean gameProgressionNeeded = this.activeSessionDAO.update(gameCode, gamePhase, authorId, isDone);
        
        if (gameProgressionNeeded) {
            System.out.println("All players are done, progressing game state via GameSessionHelper");
            this.gameSessionHelper.updateToNextGameState(gameCode);
        }
    }

    public ActivePlayerSession nextPlayerTurn(String gameCode, String currentTurnPlayerId) {
        GameSession gameSession = gameSessionHelper.getGame(gameCode);
        ActivePlayerSession activePlayerSession = gameSession.getActivePlayerSession();
        activePlayerSession.setGameCode(gameCode);

        if (
            !activePlayerSession.getPlayerId().equals(currentTurnPlayerId)
        ) {
            return activePlayerSession;
        }

        activePlayerSession.getIsPlayerDoneWithTurn().put(currentTurnPlayerId, true);

        if (activePlayerSession.getIsPlayerDoneWithTurn().values().stream().allMatch(doneWithTurn -> doneWithTurn)) {
            activePlayerSession.resetPlayerDoneWithTurn(gameSession.getPlayers());
        }

        List<Player> playersInCurrentTurnOrder = getPlayersInCurrentTurnOrder(gameSession, currentTurnPlayerId);

        for (Player nextPlayer : playersInCurrentTurnOrder) {
            String nextPlayerId = nextPlayer.getAuthorId();

            boolean turnDone = activePlayerSession.getIsPlayerDoneWithTurn().get(nextPlayerId);

            if (!turnDone) {
                activePlayerSession.resetActivePlayerSession();
                activePlayerSession.setPlayerId(nextPlayerId);
                activePlayerSession.setSetNextPlayerTurn(false);
                break;
            }
        }

        return activePlayerSession;
    }

    public ActivePlayerSession setFirstPlayerTurn(String gameCode) {
        GameSession gameSession = this.gameSessionHelper.getGame(gameCode);
        ActivePlayerSession activePlayerSession = gameSession.getActivePlayerSession();
        activePlayerSession.setGameCode(gameCode);

        String firstPlayerTurnId = gameSession.getPlayers().stream()
                .min(Comparator.comparing(Player::getJoinedAt)).get().getAuthorId();
        activePlayerSession.setPlayerId(firstPlayerTurnId);
        activePlayerSession.setGameCode(gameCode);
        activePlayerSession.setSetNextPlayerTurn(false);
        return activeSessionDAO.update(activePlayerSession);
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
