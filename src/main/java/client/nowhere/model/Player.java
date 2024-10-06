package client.nowhere.model;

import java.util.UUID;

public class Player {

    private String authorId;
    private String userName;
    private int strength = 0;
    private int dexterity = 0;
    private int charisma = 0;
    private int intellect = 0;
    private int wealth = 0;
    private String gameCode;

    public Player() { }

    public Player(String gameCode, String userName) {
        this.gameCode = gameCode;
        this.userName = userName;
        this.authorId = UUID.randomUUID().toString();
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public void createRandomAuthorId() {
        this.authorId = UUID.randomUUID().toString();
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public int getDexterity() {
        return dexterity;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    public int getCharisma() {
        return charisma;
    }

    public void setCharisma(int charisma) {
        this.charisma = charisma;
    }

    public int getIntellect() {
        return intellect;
    }

    public void setIntellect(int intellect) {
        this.intellect = intellect;
    }

    public int getWealth() {
        return wealth;
    }

    public void setWealth(int wealth) {
        this.wealth = wealth;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

}
