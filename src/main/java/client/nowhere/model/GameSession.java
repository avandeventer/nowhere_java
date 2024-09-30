package client.nowhere.model;

import java.util.ArrayList;

public class GameSession {

    String gameCode;
    ArrayList<Player> players;
    GameState gameState;

    public GameSession(String gameCode) {
        this.gameCode = gameCode;
    }

    public GameSession(String gameCode, GameState gameState) {
        this.gameCode = gameCode;
        this.gameState = gameState;
    }


    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public ArrayList<Player> getPlayers() {
        return players;
    }

    public void setPlayers(ArrayList<Player> players) {
        this.players = players;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

}
