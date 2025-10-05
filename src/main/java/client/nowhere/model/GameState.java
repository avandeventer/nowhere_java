package client.nowhere.model;

public enum GameState {
        INIT,
        WHERE_ARE_WE,
        WHERE_ARE_WE_VOTE,
        WHERE_ARE_WE_VOTE_WINNER,
        WHAT_DO_WE_FEAR,
        WHAT_DO_WE_FEAR_VOTE,
        WHAT_DO_WE_FEAR_VOTE_WINNER,
        WHO_ARE_WE,
        WHO_ARE_WE_VOTE,
        WHO_ARE_WE_VOTE_WINNER,
        WHAT_IS_OUR_GOAL,
        WHAT_IS_OUR_GOAL_VOTE,
        WHAT_IS_OUR_GOAL_VOTE_WINNER,
        WHAT_ARE_WE_CAPABLE_OF,
        WHAT_ARE_WE_CAPABLE_OF_VOTE,
        WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS,
        WHERE_CAN_WE_GO,
        GENERATE_OCCUPATION_AUTHORS,
        WHAT_OCCUPATIONS_ARE_THERE,
        PREAMBLE,
        LOCATION_SELECT,
        GENERATE_WRITE_PROMPT_AUTHORS,
        WRITE_PROMPTS,
        GENERATE_WRITE_OPTION_AUTHORS,
        WRITE_OPTIONS,
        ROUND1,
        GENERATE_WRITE_PROMPT_AUTHORS_AGAIN,
        PREAMBLE_AGAIN,
        WRITE_PROMPTS_AGAIN,
        LOCATION_SELECT_AGAIN,
        GENERATE_WRITE_OPTION_AUTHORS_AGAIN,
        WRITE_OPTIONS_AGAIN,
        ROUND2,
        ENDING_PREAMBLE,
        RITUAL,
        GENERATE_ENDINGS,
        WRITE_ENDINGS,
        ENDING,
        FINALE;

        public GameState getNextGameState() {
                switch (this) {
                        case INIT -> {
                                return GameState.WHERE_ARE_WE;
                        }
                        case WHERE_ARE_WE -> {
                                return GameState.WHERE_ARE_WE_VOTE;
                        }
                        case WHERE_ARE_WE_VOTE -> {
                                return GameState.WHERE_ARE_WE_VOTE_WINNER;
                        }
                        case WHERE_ARE_WE_VOTE_WINNER -> {
                                return GameState.WHAT_DO_WE_FEAR;
                        }
                        case WHAT_DO_WE_FEAR -> {
                                return GameState.WHAT_DO_WE_FEAR_VOTE;
                        }
                        case WHAT_DO_WE_FEAR_VOTE -> {
                                return GameState.WHAT_DO_WE_FEAR_VOTE_WINNER;
                        }
                        case WHAT_DO_WE_FEAR_VOTE_WINNER -> {
                                return GameState.WHO_ARE_WE;
                        }
                        case WHO_ARE_WE -> {
                                return GameState.WHO_ARE_WE_VOTE;
                        }
                        case WHO_ARE_WE_VOTE -> {
                                return GameState.WHO_ARE_WE_VOTE_WINNER;
                        }
                        case WHO_ARE_WE_VOTE_WINNER -> {
                                return GameState.WHAT_IS_OUR_GOAL;
                        }
                        case WHAT_IS_OUR_GOAL -> {
                                return GameState.WHAT_IS_OUR_GOAL_VOTE;
                        }
                        case WHAT_IS_OUR_GOAL_VOTE -> {
                                return GameState.WHAT_IS_OUR_GOAL_VOTE_WINNER;
                        }
                        case WHAT_IS_OUR_GOAL_VOTE_WINNER -> {
                                return GameState.WHAT_ARE_WE_CAPABLE_OF;
                        }
                        case WHAT_ARE_WE_CAPABLE_OF -> {
                                return GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE;
                        }
                        case WHAT_ARE_WE_CAPABLE_OF_VOTE -> {
                                return GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS;
                        }
                        case WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS -> {
                                return GameState.WHERE_CAN_WE_GO;
                        }
                        case WHERE_CAN_WE_GO -> {
                                return GameState.GENERATE_OCCUPATION_AUTHORS;
                        }
                        case GENERATE_OCCUPATION_AUTHORS -> {
                                return GameState.WHAT_OCCUPATIONS_ARE_THERE;
                        }
                        case WHAT_OCCUPATIONS_ARE_THERE -> {
                                return GameState.PREAMBLE;
                        }
                        case PREAMBLE -> {
                                return GameState.LOCATION_SELECT;
                        }
                        case LOCATION_SELECT -> {
                                return GameState.GENERATE_WRITE_PROMPT_AUTHORS;
                        }
                        case GENERATE_WRITE_PROMPT_AUTHORS -> {
                                return GameState.WRITE_PROMPTS;
                        }
                        case WRITE_PROMPTS -> {
                                return GameState.GENERATE_WRITE_OPTION_AUTHORS;
                        }
                        case GENERATE_WRITE_OPTION_AUTHORS -> {
                                return GameState.WRITE_OPTIONS;
                        }
                        case WRITE_OPTIONS -> {
                                return GameState.ROUND1;
                        }
                        case ROUND1 -> {
                                return GameState.PREAMBLE_AGAIN;
                        }
                        case PREAMBLE_AGAIN -> {
                                return GameState.LOCATION_SELECT_AGAIN;
                        }
                        case LOCATION_SELECT_AGAIN -> {
                                return GameState.GENERATE_WRITE_PROMPT_AUTHORS_AGAIN;
                        }
                        case GENERATE_WRITE_PROMPT_AUTHORS_AGAIN -> {
                                return GameState.WRITE_PROMPTS_AGAIN;
                        }
                        case WRITE_PROMPTS_AGAIN -> {
                                return GameState.GENERATE_WRITE_OPTION_AUTHORS_AGAIN;
                        }
                        case GENERATE_WRITE_OPTION_AUTHORS_AGAIN -> {
                                return GameState.WRITE_OPTIONS_AGAIN;
                        }
                        case WRITE_OPTIONS_AGAIN -> {
                                return GameState.ROUND2;
                        }
                        case ROUND2 -> {
                                return GameState.ENDING_PREAMBLE;
                        }
                        case ENDING_PREAMBLE -> {
                                return GameState.RITUAL;
                        }
                        case RITUAL -> {
                                return GameState.GENERATE_ENDINGS;
                        }
                        case GENERATE_ENDINGS -> {
                                return GameState.WRITE_ENDINGS;
                        }
                        case WRITE_ENDINGS -> {
                                return GameState.ENDING;
                        }
                        case ENDING -> {
                                return GameState.FINALE;
                        }
                        default -> {
                                return GameState.INIT;
                        }
                }
        }
}