package client.nowhere.model;

public enum GameState {
        INIT,
        WHERE_ARE_WE,
        WHERE_ARE_WE_VOTE,
        WHERE_ARE_WE_VOTE_WINNER,
        WHAT_DO_WE_FEAR,
        WHAT_DO_WE_FEAR_VOTE,
        WHAT_DO_WE_FEAR_VOTE_WINNER,
        WHAT_IS_COMING,
        WHAT_IS_COMING_VOTE,
        WHAT_IS_COMING_VOTE_WINNER,
        WHO_ARE_WE,
        WHO_ARE_WE_VOTE,
        WHO_ARE_WE_VOTE_WINNER,
        WHAT_ARE_WE_CAPABLE_OF,
        WHAT_ARE_WE_CAPABLE_OF_VOTE,
        WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS,
        WHAT_WILL_BECOME_OF_US,
        WHAT_WILL_BECOME_OF_US_VOTE,
        WHAT_WILL_BECOME_OF_US_VOTE_WINNER,
        GENERATE_LOCATION_AUTHORS,
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
        WRITE_ENDING_TEXT,
        ENDING_PREAMBLE,
        RITUAL,
        GENERATE_ENDINGS,
        WRITE_ENDINGS,
        ENDING,
        FINALE,

        //Dungeon Mode:
        SET_ENCOUNTERS,
        SET_ENCOUNTERS_VOTING,
        SET_ENCOUNTERS_WINNERS,
        WHAT_HAPPENS_HERE,
        WHAT_HAPPENS_HERE_VOTING,
        WHAT_HAPPENS_HERE_WINNER,
        WHAT_CAN_WE_TRY,
        WHAT_CAN_WE_TRY_VOTING,
        WHAT_CAN_WE_TRY_WINNERS,
        HOW_DOES_THIS_RESOLVE,
        HOW_DOES_THIS_RESOLVE_VOTING,
        HOW_DOES_THIS_RESOLVE_WINNERS,
        MAKE_CHOICE_VOTING,
        MAKE_CHOICE_WINNER,
        NAVIGATE_VOTING,
        NAVIGATE_WINNER;

        public GameState getNextGameState(GameMode gameMode) {
            if (gameMode == GameMode.TOWN_MODE) {
                return getNextGameStateTownMode();
            } else {
                return getNextGameStateDungeonMode();
            }
        }

    private GameState getNextGameStateDungeonMode() {
        switch (this) {
            case INIT -> {
                return GameState.PREAMBLE;
            }
            case PREAMBLE -> {
                return GameState.SET_ENCOUNTERS;
            }
            case SET_ENCOUNTERS -> {
                return GameState.SET_ENCOUNTERS_VOTING;
            }
            case SET_ENCOUNTERS_VOTING -> {
                return GameState.SET_ENCOUNTERS_WINNERS;
            }
            case SET_ENCOUNTERS_WINNERS, NAVIGATE_WINNER -> {
                return GameState.WHAT_HAPPENS_HERE;
            }
            case WHAT_HAPPENS_HERE -> {
                return GameState.WHAT_HAPPENS_HERE_VOTING;
            }
            case WHAT_HAPPENS_HERE_VOTING -> {
                return GameState.WHAT_HAPPENS_HERE_WINNER;
            }
            case WHAT_HAPPENS_HERE_WINNER -> {
                return GameState.WHAT_CAN_WE_TRY;
            }
            case WHAT_CAN_WE_TRY -> {
                return GameState.WHAT_CAN_WE_TRY_VOTING;
            }
            case WHAT_CAN_WE_TRY_VOTING -> {
                return GameState.WHAT_CAN_WE_TRY_WINNERS;
            }
            case WHAT_CAN_WE_TRY_WINNERS -> {
                return GameState.HOW_DOES_THIS_RESOLVE;
            }
            case HOW_DOES_THIS_RESOLVE -> {
                return GameState.HOW_DOES_THIS_RESOLVE_VOTING;
            }
            case HOW_DOES_THIS_RESOLVE_VOTING -> {
                return GameState.HOW_DOES_THIS_RESOLVE_WINNERS;
            }
            case HOW_DOES_THIS_RESOLVE_WINNERS -> {
                return GameState.MAKE_CHOICE_VOTING;
            }
            case MAKE_CHOICE_VOTING -> {
                return GameState.MAKE_CHOICE_WINNER;
            }
            case MAKE_CHOICE_WINNER -> {
                return GameState.NAVIGATE_VOTING;
            }
            case NAVIGATE_VOTING -> {
                return GameState.NAVIGATE_WINNER;
            }
            default -> {
                return GameState.INIT;
            }
        }
    }

    private GameState getNextGameStateTownMode() {
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
                        return GameState.WHAT_IS_COMING;
                    }
                    case WHAT_IS_COMING -> {
                        return GameState.WHAT_IS_COMING_VOTE;
                    }
                    case WHAT_IS_COMING_VOTE -> {
                        return GameState.WHAT_IS_COMING_VOTE_WINNER;
                    }
                    case WHAT_IS_COMING_VOTE_WINNER -> {
                        return GameState.WHO_ARE_WE;
                    }
                    case WHO_ARE_WE -> {
                        return GameState.WHO_ARE_WE_VOTE;
                    }
                    case WHO_ARE_WE_VOTE -> {
                        return GameState.WHO_ARE_WE_VOTE_WINNER;
                    }
                    case WHO_ARE_WE_VOTE_WINNER -> {
                        return GameState.WHAT_ARE_WE_CAPABLE_OF;
                    }
                    case WHAT_ARE_WE_CAPABLE_OF -> {
                        return GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE;
                    }
                    case WHAT_ARE_WE_CAPABLE_OF_VOTE -> {
                        return GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS;
                    }
                    case WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS -> {
                        return GameState.WHAT_WILL_BECOME_OF_US;
                    }
                    case WHAT_WILL_BECOME_OF_US -> {
                        return GameState.WHAT_WILL_BECOME_OF_US_VOTE;
                    }
                    case WHAT_WILL_BECOME_OF_US_VOTE -> {
                        return GameState.WHAT_WILL_BECOME_OF_US_VOTE_WINNER;
                    }
                    case WHAT_WILL_BECOME_OF_US_VOTE_WINNER -> {
                        return GameState.GENERATE_LOCATION_AUTHORS;
                    }
                    case GENERATE_LOCATION_AUTHORS -> {
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

    /**
         * Gets the phase ID for this game state.
         * Returns the phase identifier that groups related game states together.
         * @return The phase ID string, or null if this game state doesn't belong to a collaborative text phase
         */
        public GameState getPhaseId() {
                return switch (this) {
                        case WHERE_ARE_WE, WHERE_ARE_WE_VOTE, WHERE_ARE_WE_VOTE_WINNER -> WHERE_ARE_WE;
                        case WHAT_DO_WE_FEAR, WHAT_DO_WE_FEAR_VOTE, WHAT_DO_WE_FEAR_VOTE_WINNER -> WHAT_DO_WE_FEAR;
                        case WHO_ARE_WE, WHO_ARE_WE_VOTE, WHO_ARE_WE_VOTE_WINNER -> WHO_ARE_WE;
                        case WHAT_IS_COMING, WHAT_IS_COMING_VOTE, WHAT_IS_COMING_VOTE_WINNER -> WHAT_IS_COMING;
                        case WHAT_ARE_WE_CAPABLE_OF, WHAT_ARE_WE_CAPABLE_OF_VOTE, WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS -> WHAT_ARE_WE_CAPABLE_OF;
                        case WHAT_WILL_BECOME_OF_US, WHAT_WILL_BECOME_OF_US_VOTE, WHAT_WILL_BECOME_OF_US_VOTE_WINNER -> WHAT_WILL_BECOME_OF_US;
                        case SET_ENCOUNTERS, SET_ENCOUNTERS_VOTING, SET_ENCOUNTERS_WINNERS -> SET_ENCOUNTERS;
                        case WHAT_HAPPENS_HERE, WHAT_HAPPENS_HERE_VOTING, WHAT_HAPPENS_HERE_WINNER -> WHAT_HAPPENS_HERE;
                        case WHAT_CAN_WE_TRY, WHAT_CAN_WE_TRY_VOTING, WHAT_CAN_WE_TRY_WINNERS -> WHAT_CAN_WE_TRY;
                        case HOW_DOES_THIS_RESOLVE, HOW_DOES_THIS_RESOLVE_VOTING, HOW_DOES_THIS_RESOLVE_WINNERS -> HOW_DOES_THIS_RESOLVE;
                        case MAKE_CHOICE_VOTING, MAKE_CHOICE_WINNER -> MAKE_CHOICE_VOTING;
                        case NAVIGATE_VOTING, NAVIGATE_WINNER -> NAVIGATE_VOTING;
                        default -> null;
                };
        }

        /**
         * Gets the base phase information for this game state.
         * @param entityName The name of the entity (used in some phase instructions)
         * @return PhaseBaseInfo containing phase question, instructions, collaborative mode, and related game states
         */
        public PhaseBaseInfo getPhaseBaseInfo(String entityName) {
                // Determine which phase group this game state belongs to using getPhaseId()
                GameState phaseId = getPhaseId();
                if (phaseId == WHERE_ARE_WE) {
                        return new PhaseBaseInfo(
                                "Where are we?",
                                "We will begin by describing our world.",
                                CollaborativeMode.SHARE_TEXT,
                                WHERE_ARE_WE,
                                WHERE_ARE_WE_VOTE,
                                WHERE_ARE_WE_VOTE_WINNER
                        );
                }
                if (phaseId == WHAT_DO_WE_FEAR) {
                        return new PhaseBaseInfo(
                                "What do we fear?",
                                "What do we fear? What person, group, or entity holds power in this world?",
                                CollaborativeMode.RAPID_FIRE,
                                WHAT_DO_WE_FEAR,
                                WHAT_DO_WE_FEAR_VOTE,
                                WHAT_DO_WE_FEAR_VOTE_WINNER
                        );
                }
                if (phaseId == WHO_ARE_WE) {
                        return new PhaseBaseInfo(
                                "Who are we?",
                                "Define who we are together. What is our goal?",
                                CollaborativeMode.SHARE_TEXT,
                                WHO_ARE_WE,
                                WHO_ARE_WE_VOTE,
                                WHO_ARE_WE_VOTE_WINNER
                        );
                }
                if (phaseId == WHAT_IS_COMING) {
                        return new PhaseBaseInfo(
                                "What is coming?",
                                "An event will occur at the end of the season where we will be judged by " + entityName + ". What must we each do when they arrive to ensure our success or survival?",
                                CollaborativeMode.SHARE_TEXT,
                                WHAT_IS_COMING,
                                WHAT_IS_COMING_VOTE,
                                WHAT_IS_COMING_VOTE_WINNER
                        );
                }
                if (phaseId == WHAT_ARE_WE_CAPABLE_OF) {
                        return new PhaseBaseInfo(
                                "What are we capable of?",
                                "We will need certain skills in order to overcome. List anything you think we will need to be good at to survive.",
                                CollaborativeMode.RAPID_FIRE,
                                WHAT_ARE_WE_CAPABLE_OF,
                                WHAT_ARE_WE_CAPABLE_OF_VOTE,
                                WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS
                        );
                }
                if (phaseId == WHAT_WILL_BECOME_OF_US) {
                        return new PhaseBaseInfo(
                                "What will become of us?",
                                "What will become of us when our confrontation with " + entityName + " is over?",
                                CollaborativeMode.SHARE_TEXT,
                                WHAT_WILL_BECOME_OF_US,
                                WHAT_WILL_BECOME_OF_US_VOTE,
                                WHAT_WILL_BECOME_OF_US_VOTE_WINNER
                        );
                }
                if (this == WRITE_ENDING_TEXT) {
                        // WRITE_ENDING_TEXT only has a collaborating state, no voting or winning
                        return new PhaseBaseInfo(
                                "How will our story end?",
                                "Based on how well we have done as a group, write the ending text that will be displayed. This will determine how our story concludes.",
                                CollaborativeMode.SHARE_TEXT,
                                WRITE_ENDING_TEXT,
                                WRITE_ENDING_TEXT, // Use same state as fallback
                                WRITE_ENDING_TEXT  // Use same state as fallback
                        );
                }
                if (phaseId == SET_ENCOUNTERS) {
                        return new PhaseBaseInfo(
                                "What could we encounter here?",
                                "Name some things that we might see on our adventure through this place",
                                CollaborativeMode.RAPID_FIRE,
                                SET_ENCOUNTERS,
                                SET_ENCOUNTERS_VOTING,
                                SET_ENCOUNTERS_WINNERS
                        );
                }
                if (phaseId == WHAT_HAPPENS_HERE) {
                        return new PhaseBaseInfo(
                                "What happens here?",
                                "Read the prompt and add any description of it that you feel applies!",
                                CollaborativeMode.SHARE_TEXT,
                                WHAT_HAPPENS_HERE,
                                WHAT_HAPPENS_HERE_VOTING,
                                WHAT_HAPPENS_HERE_WINNER
                        );
                }
                if (phaseId == WHAT_CAN_WE_TRY) {
                        return new PhaseBaseInfo(
                                "What can we try?",
                                "What are some actions we could try to take? List them!",
                                CollaborativeMode.RAPID_FIRE,
                                WHAT_CAN_WE_TRY,
                                WHAT_CAN_WE_TRY_VOTING,
                                WHAT_CAN_WE_TRY_WINNERS
                        );
                }
                if (phaseId == HOW_DOES_THIS_RESOLVE) {
                        return new PhaseBaseInfo(
                                "How does this resolve?",
                                "Look at the action you've been assigned and describe what happens if we choose it!",
                                CollaborativeMode.SHARE_TEXT,
                                HOW_DOES_THIS_RESOLVE,
                                HOW_DOES_THIS_RESOLVE_VOTING,
                                HOW_DOES_THIS_RESOLVE_WINNERS
                        );
                }  
                if (phaseId == MAKE_CHOICE_VOTING) {
                    return new PhaseBaseInfo(
                        "What do we choose?",
                        "What is the next step in our adventure?",
                        CollaborativeMode.SHARE_TEXT,
                        null,
                        MAKE_CHOICE_VOTING,
                        MAKE_CHOICE_WINNER
                    );
                }
                if (phaseId == NAVIGATE_VOTING) {
                    return new PhaseBaseInfo(
                        "Where do we go now?",
                        "Seek " + entityName + " or just go in the most interesting direction!",
                        CollaborativeMode.SHARE_TEXT,
                        null,
                        NAVIGATE_VOTING,
                        NAVIGATE_WINNER
                    );
                }
                // Default fallback
                return new PhaseBaseInfo(
                        "Collaborative Writing",
                        "Work together to build your story!",
                        CollaborativeMode.SHARE_TEXT,
                        INIT, // Use INIT as fallback
                        INIT, // Use INIT as fallback
                        INIT  // Use INIT as fallback
                );
        }
}