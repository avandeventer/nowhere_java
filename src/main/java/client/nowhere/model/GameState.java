package client.nowhere.model;

public enum GameState {
        INIT,
        START,
        LOCATION_SELECT,
        WRITE_PROMPTS,
        GENERATE_WRITE_OPTION_AUTHORS,
        WRITE_OPTIONS,
        ROUND1,
        START_PHASE2,
        WRITE_PROMPTS_AGAIN,
        LOCATION_SELECT_AGAIN,
        GENERATE_WRITE_OPTION_AUTHORS_AGAIN,
        WRITE_OPTIONS_AGAIN,
        ROUND2,
        RITUAL,
        GENERATE_ENDINGS,
        WRITE_ENDINGS,
        ENDING,
        FINALE;

        public GameState getNextGameState() {
                switch (this) {
                        case INIT -> {
                                return GameState.LOCATION_SELECT;
                        }
                        case LOCATION_SELECT -> {
                                return GameState.START;
                        }
                        case START -> {
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
                                return GameState.LOCATION_SELECT_AGAIN;
                        }
                        case LOCATION_SELECT_AGAIN -> {
                                return GameState.START_PHASE2;
                        }
                        case START_PHASE2 -> {
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