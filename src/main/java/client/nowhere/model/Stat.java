package client.nowhere.model;

public enum Stat {
    STRENGTH (
            new StatType(
                    "6041168f-6835-46ca-be01-0e69956c4393", "strength"
            )
    ),
    DEXTERITY (
            new StatType("fcc8d013-6cf2-44ef-939a-71ac753c0b41", "dexterity")
    ),
    CHARISMA (
            new StatType("8d4d43e1-4d75-4efa-a084-ce42e6936cb1", "charisma")
    ),
    INTELLECT (
            new StatType("53b5d8ae-6aa9-4cab-868d-b9a6e279988c", "intellect")
    ),
    WEALTH (
            new StatType("45ffe04a-9050-4c96-a541-cc1f55697bcf", "wealth")
    ),
    MAGIC (
            new StatType("eb687e49-488a-445b-860d-cd924e504a51", "magic")
    ),
    FAVOR (
            new StatType("cd872364-a893-4b87-9f42-3e30de40a93e", "favor")
    );

    private final StatType statType;

    Stat(StatType statType) {
        this.statType = statType;
    }

    public StatType getStatType() {
        return statType;
    };
}

