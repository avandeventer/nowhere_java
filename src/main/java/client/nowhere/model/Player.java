package client.nowhere.model;

import java.util.UUID;

public class Player {

    private String authorId;
    private String userName;
    private int strength = 4;
    private int dexterity = 4;
    private int charisma = 4;
    private int intellect = 4;
    private int wealth = 4;
    private int magic = 4;
    private int favor = 4;
    private String gameCode;
    private boolean isFirstPlayer = false;

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

    public int getMagic() {
        return magic;
    }

    public void setMagic(int magic) {
        this.magic = magic;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public int getFavor() {
        return favor;
    }

    public void setFavor(int favor) {
        this.favor = favor;
    }

    public int getStatByEnum (Stat stat) {
        switch (stat) {
            case CHARISMA:
                return this.getCharisma();
            case STRENGTH:
                return this.getStrength();
            case DEXTERITY:
                return this.getDexterity();
            case FAVOR:
                return this.getFavor();
            case INTELLECT:
                return this.getIntellect();
            case MAGIC:
                return this.getMagic();
            case WEALTH:
                return this.getWealth();
            default:
                return 0;
        }
    }

    public void updatePlayer(Player player) {
        this.setStrength(player.getStrength());
        this.setIntellect(player.getIntellect());
        this.setCharisma(player.getCharisma());
        this.setDexterity(player.getDexterity());
        this.setWealth(player.getWealth());
        this.setMagic(player.getMagic());
        this.setFavor(player.getFavor());
    }

    public boolean getFirstPlayer() {
        return this.isFirstPlayer;
    }

    public void setFirstPlayer(boolean isFirstPlayer) {
        this.isFirstPlayer = isFirstPlayer;
    }
}
