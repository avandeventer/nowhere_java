package client.nowhere.exception;

public class GameStateException extends RuntimeException {
    public GameStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public GameStateException(String message) {
        super(message);
    }

}
