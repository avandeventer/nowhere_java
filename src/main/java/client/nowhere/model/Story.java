package client.nowhere.model;

import client.nowhere.constants.AuthorConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Story {

    private String storyId = "";
    private String prompt = "";
    private String authorId = "";
    private String outcomeAuthorId = "";
    private boolean prequelStorySucceeded = false;
    private String prequelStoryId = "";
    private Location location;
    private List<Option> options;
    private String gameCode = "";
    private List<Repercussion> successRepercussions;
    private List<Repercussion> failureRepercussions;
    private List<String> prequelOutcomeDisplay;

    //Temporary player variables
    private boolean visited = false;
    private String playerId = "";
    private String selectedOptionId = "";
    private boolean playerSucceeded = false;
    private String prequelStoryPlayerId = "";


    public Story () {
        this.storyId = UUID.randomUUID().toString();
        if(this.location != null) {
            AdventureMap adventureMap = new AdventureMap();
            this.location = adventureMap.getLocations().get(location.getLocationId());
        }
    }

    public Story (List<Option> options) {
        this.options = options;
    }

    public Story(String gameCode) {
        this.gameCode = gameCode;
        this.storyId = UUID.randomUUID().toString();
    }

    public Story(
            String gameSessionCode,
            Location location,
            String outcomeAuthorId,
            String playerAuthorId
    ) {
        this.gameCode = gameSessionCode;
        this.storyId = UUID.randomUUID().toString();
        randomizeNewStory();
        this.location = location;
        this.authorId = playerAuthorId;
        this.outcomeAuthorId = outcomeAuthorId;
    }

    public Story(
            String gameSessionCode,
            Location location,
            String outcomeAuthorId,
            String playerAuthorId,
            String prequelStoryId,
            String playerPrequelStoryId,
            boolean prequelStorySucceeded
    ) {
        this.gameCode = gameSessionCode;
        this.storyId = UUID.randomUUID().toString();
        randomizeNewStory();
        this.location = location;
        this.authorId = playerAuthorId;
        this.outcomeAuthorId = outcomeAuthorId;
        this.prequelStoryId = prequelStoryId;
        this.prequelStoryPlayerId = playerPrequelStoryId;
        this.prequelStorySucceeded = prequelStorySucceeded;
    }

    public void randomizeNewStory() {
        this.prompt = "";
        int minDC = 1;
        int maxDC = 10;

        Option optionOne = new Option();
        optionOne.randomizeOptionStats(minDC, maxDC);

        if(optionOne.getStatDC() >= 7) {
            maxDC = 6;
        }

        if(optionOne.getStatDC() <= 3) {
            minDC = 3;
        }

        Option optionTwo = new Option();
        optionTwo.randomizeOptionStats(minDC, maxDC);
        while (optionTwo.getStatRequirement() == optionOne.getStatRequirement()) {
            optionTwo.setStatRequirement(Stat.values()[ThreadLocalRandom.current().nextInt(Stat.values().length)]);
        }

        this.options = new ArrayList<>();
        options.add(optionOne);
        options.add(optionTwo);
    }

    public void defaultStory(String gameCode, int locationIndex) {
        this.randomizeNewStory();
        String creatureEncountered = "";
        this.gameCode = gameCode;
        this.setAuthorId(AuthorConstants.DEFAULT);
        this.setOutcomeAuthorId(AuthorConstants.DEFAULT);
        AdventureMap adventureMap = new AdventureMap();
        this.setLocation(adventureMap.getLocations().get(locationIndex));

        switch(this.options.get(0).getStatRequirement()) {
            case CHARISMA:
                creatureEncountered = "a beautiful translucent person";
                this.options.get(0).setOptionText("Try to sing to it");
                this.options.get(0).setSuccessText("Your singing encourages the ghostly person. They seem satisfied and join in song with you.");
                this.options.get(0).setFailureText("They are immediately disgusted by your performance. They disappear before you even finish. In your shame you decide not to go perform any time soon.");
                break;
            case MAGIC:
                creatureEncountered = "a great winged spirit";
                this.options.get(0).setOptionText("Cast a binding hex");
                this.options.get(0).setSuccessText("The spirit twitches and resists, but is eventually bound by your words and grants you a boon against its wishes. Good job?");
                this.options.get(0).setFailureText("The creature sees what you are attempting to do and casts its own hex. You wake up the next day feeling terrible.");
                break;
            case WEALTH:
                creatureEncountered = "a tiny and mischievous nixie";
                this.options.get(0).setOptionText("Offer the nixie some coin");
                this.options.get(0).setSuccessText("You give it some coin and it is immediately pleased. It spins in a circle, spewing a strange dust upon you. You feel yourself change for the better.");
                this.options.get(0).setFailureText("The creature resents your offering. Perhaps you should not have been so stingy. It sneezes upon you before allowing itself to become absorbed into a nearby tree stump. You feel bad.");
                break;
            case STRENGTH:
                creatureEncountered = "an enormous and strange furry beast";
                this.options.get(0).setOptionText("Attempt to overpower the beast");
                this.options.get(0).setSuccessText("With great effort you pin the beast down. It is impressed. It bows to you before bucking and running to freedom.");
                this.options.get(0).setFailureText("The beast is not phased by your assault and seems confused about why you are trying to fight it. Why did you do that?");
                break;
            case DEXTERITY:
                creatureEncountered = "a shifting shadow";
                this.options.get(0).setOptionText("Try to run");
                this.options.get(0).setSuccessText("The shadow gives chase, but your agile movements manage to get you free of it. Eventually you turn to look behind you and you only see the shadows cast by the plant life. Did you ever actually see it at all?");
                this.options.get(0).setFailureText("You jump from one side of the road to the other, but the shadow catches up to you easily. It covers your whole body and seeps into you. You're not sure what just happened.");
                break;
            case INTELLECT:
                creatureEncountered = "a visage of your deceased father";
                this.options.get(0).setOptionText("Ask him a question about yourself");
                this.options.get(0).setSuccessText("The strange mirage attempts to answer in a facsimile of your father's voice, but it provides details that are strange and out of place. You realize that you are dealing with a trickster spirit and speak its name out loud. It screams and disappears. You feel sad, but victorious.");
                this.options.get(0).setFailureText("The question you asked is very vague and the mirage answers it perfectly. You are convinced that this is your father, returned from the dead! You willingly provide it with some of your essence.");
                break;
            default:
                creatureEncountered = "fish";
                break;
        }

        String predicament = "";

        switch (this.options.get(1).getStatRequirement()) {
            case CHARISMA -> {
                predicament = "tells you a story about its life";
                this.options.get(1).setOptionText("Offer your own story in return");
                this.options.get(1).setSuccessText("The two of you swap stories well into the night. You almost don't notice when your companion fades away. You feel lonely, but fulfilled.");
                this.options.get(1).setFailureText("Your companion is incensed that you would talk about yourself instead of take an interest. You jerk.");
            }
            case MAGIC -> {
                predicament = "begins quietly uttering phrases in an unknown language";
                this.options.get(1).setOptionText("Utter the phrases back");
                this.options.get(1).setSuccessText("Your speech overlaps with theirs in a chaotic mess of incantation, but you reach the final word first. Their eyes widen with fear and they scream while reality around them collapses. Good job!");
                this.options.get(1).setFailureText("You do your best to mimic their speech, but you don't know the spell. You do manage to confuse them a bit and perhaps the spell that occurs as they vanish is lessened a bit from their annoyance.");
            }
            case WEALTH -> {
                predicament = "begs you for food";
                this.options.get(1).setOptionText("Offer what you have");
                this.options.get(1).setSuccessText("They devour the food hungrily. You see the genuine gleam in their eyes as they finish, their belly filled. Somewhere in the ether, a force for morality gives you a gift.");
                this.options.get(1).setFailureText("They stare at the single grape you procure from your cloak with irony and then hatred. They spit in your face.");
            }
            case STRENGTH -> {
                predicament = "is tethered to an enormous troll";
                this.options.get(1).setOptionText("Try to cut the tether");
                this.options.get(1).setSuccessText("You slice through the tether like butter and the troll shrieks with agony. Together, the two of you use the tether to trip the troll up and flee. You high-five! Weird!");
                this.options.get(1).setFailureText("The troll makes eye contact with you as your knife hits the tether with a plunk. It remains unbroken. The troll decks you soundly in the face and its prisoner utters a sullen 'Thanks a lot.'");
            }
            case DEXTERITY -> {
                predicament = "is flitting from one side of the road to the other";
                this.options.get(1).setSuccessText("Your speed is outmatched and your prey leaps sideways directly into the spot you predicted they would. You guess their true fey form and request a wish out of them. They look at you, defeated and grant it.");
                this.options.get(1).setFailureText("You attempt to predict their movements, but you're not quick enough. You trip and fall into a ditch and they laugh at you. You feel silly.");
                this.options.get(1).setOptionText("Try to catch them!");
            }
            case INTELLECT -> {
                predicament = "asks you how you would run the harvest if you were a god";
                this.options.get(1).setOptionText("Say you would only ask for a sacrifice if there were too many mouths to feed");
                this.options.get(1).setSuccessText("They ponder for a moment and then... they agree with you. This is a fair way to rule. They say that you should be running things around her and you agree!");
                this.options.get(1).setFailureText("They ponder for a moment and then... they smirk and begin to laugh. 'Such a simple way to look at things.' You feel very stupid.");
            }
            default -> {
                predicament = "fish";
                this.options.get(1).setSuccessText("fish");
                this.options.get(1).setFailureText("fish");
            }
        }

        Stat optionOneStat = this.options.get(0).getStatRequirement();
        Stat optionTwoStat = this.options.get(1).getStatRequirement();
        this.options.get(0).getSuccessResults().get(0).setImpactedStat(optionTwoStat);
        this.options.get(0).getFailureResults().get(0).setImpactedStat(optionTwoStat);
        this.options.get(1).getSuccessResults().get(0).setImpactedStat(optionOneStat);
        this.options.get(0).getFailureResults().get(0).setImpactedStat(optionTwoStat);

        this.setPrompt("As you leave and travel upon the road you encounter "
                + creatureEncountered + " which "
                + predicament + ". How do you proceed?"
        );
    }


    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<Option> getOptions() {
        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public List<Repercussion> getSuccessRepercussions() {
        return successRepercussions;
    }

    public void setSuccessRepercussions(List<Repercussion> successRepercussions) {
        this.successRepercussions = successRepercussions;
    }

    public List<Repercussion> getFailureRepercussions() {
        return failureRepercussions;
    }

    public void setFailureRepercussions(List<Repercussion> failureRepercussions) {
        this.failureRepercussions = failureRepercussions;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getOutcomeAuthorId() {
        return this.outcomeAuthorId;
    }

    public void setOutcomeAuthorId(String outcomeAuthorId) { this.outcomeAuthorId = outcomeAuthorId; }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getSelectedOptionId() {
        return selectedOptionId;
    }

    public void setSelectedOptionId(String selectedOptionId) {
        this.selectedOptionId = selectedOptionId;
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public String getPrequelStoryId() {
        return prequelStoryId;
    }

    public void setPrequelStoryId(String prequelStoryId) {
        this.prequelStoryId = prequelStoryId;
    }

    public boolean isPlayerSucceeded() {
        return playerSucceeded;
    }

    public void setPlayerSucceeded(boolean playerSucceeded) {
        this.playerSucceeded = playerSucceeded;
    }

    public boolean isPrequelStorySucceeded() {
        return prequelStorySucceeded;
    }

    public void setPrequelStorySucceeded(boolean prequelStorySucceeded) {
        this.prequelStorySucceeded = prequelStorySucceeded;
    }

    public String getPrequelStoryPlayerId() {
        return prequelStoryPlayerId;
    }

    public void setPrequelStoryPlayerId(String prequelStoryPlayerId) {
        this.prequelStoryPlayerId = prequelStoryPlayerId;
    }

    public List<String> getPrequelOutcomeDisplay() {
        return prequelOutcomeDisplay;
    }

    public void setPrequelOutcomeDisplay(List<String> prequelOutcomeDisplay) {
        this.prequelOutcomeDisplay = prequelOutcomeDisplay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Story story = (Story) o;
        return visited == story.visited && playerSucceeded == story.playerSucceeded && prequelStorySucceeded == story.prequelStorySucceeded && Objects.equals(storyId, story.storyId) && Objects.equals(prompt, story.prompt) && Objects.equals(authorId, story.authorId) && Objects.equals(outcomeAuthorId, story.outcomeAuthorId) && Objects.equals(playerId, story.playerId) && Objects.equals(selectedOptionId, story.selectedOptionId) && Objects.equals(prequelStoryId, story.prequelStoryId) && Objects.equals(prequelStoryPlayerId, story.prequelStoryPlayerId) && Objects.equals(location, story.location) && Objects.equals(options, story.options) && Objects.equals(gameCode, story.gameCode) && Objects.equals(successRepercussions, story.successRepercussions) && Objects.equals(failureRepercussions, story.failureRepercussions) && Objects.equals(prequelOutcomeDisplay, story.prequelOutcomeDisplay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storyId, visited, prompt, authorId, outcomeAuthorId, playerId, selectedOptionId, playerSucceeded, prequelStorySucceeded, prequelStoryId, prequelStoryPlayerId, location, options, gameCode, successRepercussions, failureRepercussions, prequelOutcomeDisplay);
    }

    public void resetPlayerVariables() {
        this.playerId = "";
        this.visited = false;
        this.playerSucceeded = false;
        this.selectedOptionId = "";
        if (!this.prequelStoryPlayerId.isBlank()) {
            this.prequelStoryPlayerId = AuthorConstants.GLOBAL_PLAYER_SEQUEL;
            this.location = new Location();
        }
    }
}
