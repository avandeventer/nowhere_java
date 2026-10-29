package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.helper.PhasePaths;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Component
public class GameSessionDAO {

    private static final int MAX_BATCH_WRITES = 500;

    private final Firestore db;
    private final StoryDAO storyDAO;

    @Autowired
    public GameSessionDAO(Firestore db, StoryDAO storyDAO) {
        this.db = db;
        this.storyDAO = storyDAO;
    }

    public GameSession createGameSession(
            String sessionCode,
            String userProfileId,
            AdventureMap adventureMap,
            String saveGameId,
            Integer storiesToWritePerRound,
            Integer storiesToPlayPerRound,
            GameMode gameMode
    ) {
        DocumentReference docRef = db.collection("gameSessions").document(sessionCode);

        GameSession gameSession = new GameSession(sessionCode);
        gameSession.setGameCode(sessionCode);
        gameSession.setGameState(GameState.INIT);
        gameSession.setUserProfileId(userProfileId);
        gameSession.setSaveGameId(saveGameId);
        gameSession.setStoriesToWritePerRound(storiesToWritePerRound);
        gameSession.setStoriesToPlayPerRound(storiesToPlayPerRound);
        gameSession.setAdventureMap(adventureMap);
        gameSession.setGameMode(gameMode);

        try {
            ApiFuture<WriteResult> result = docRef.set(gameSession);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return gameSession;
    }

    public GameSession createGameSession(GameSession gameSession) {
        DocumentReference docRef = db.collection("gameSessions").document(gameSession.getGameCode());

        try {
            // GameSession.stories is @Exclude'd from the game document; they get their own documents
            ApiFuture<WriteResult> result = docRef.set(gameSession);
            WriteResult asyncResponse = result.get();
            if (gameSession.getStories() != null && !gameSession.getStories().isEmpty()) {
                storyDAO.createStories(gameSession.getGameCode(), gameSession.getStories(), roundNumber(gameSession));
            }

            gameSession = getGame(gameSession.getGameCode());

            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return gameSession;
    }

    public Player joinGameSession(Player player) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(player.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("players", FieldValue.arrayUnion(player));
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return player;
    }

    public GameSession updateGameSession(GameSession gameSession) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameSession.getGameCode());
        Map<String, Object> updates = new HashMap<>();
        updates.put("gameState", gameSession.getGameState());

        if (gameSession.getSaveGameId() != null && !gameSession.getSaveGameId().isEmpty()) {
            updates.put("saveGameId", gameSession.getSaveGameId());
        }
        updates.put("activeGameStateSession", gameSession.getActiveGameStateSession());
        updates.put("roundNumber", gameSession.getRoundNumber());
        updates.put("activePlayerSession", gameSession.getActivePlayerSession());
        updates.put("totalPointsTowardsVictory", gameSession.getTotalPointsTowardsVictory());

        try {
            ApiFuture<WriteResult> result = gameSessionRef.update(updates);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return gameSession;
    }

    public List<Player> getPlayers(String gameCode) {
        GameSession game = getGame(gameCode);
        return game.getPlayers();
    }

    public GameSession getGame(String gameCode) {
        GameSession gameSession = new GameSession();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
            gameSession.setStories(storyDAO.loadStories(gameCode, roundNumber(gameSession)));
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return gameSession;
    }

    private static int roundNumber(GameSession gameSession) {
        return gameSession.getRoundNumber() == null ? 0 : gameSession.getRoundNumber();
    }

    public Player updatePlayer(Player player) {
        try {
            GameSession gameSession = getGame(player.getGameCode());
            List<Player> players = gameSession.getPlayers();
            players.stream()
                    .filter(existingPlayer -> existingPlayer.getAuthorId().equals(player.getAuthorId()))
                    .forEach(existingPlayer -> existingPlayer.updatePlayer(player));
            DocumentReference gameSessionRef = db.collection("gameSessions").document(player.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("players", players);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + asyncResponse.toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the player", e);
        }

        return player;
    }

    public Player getPlayer(String gameCode, String authorId) {
        List<Player> players = this.getPlayers(gameCode);
        return players.stream().filter(player -> player.getAuthorId().equals(authorId)).findFirst().get();
    }

    public DocumentReference getGameRef(String gameCode) {
        return db.collection("gameSessions").document(gameCode);
    }

    /**
     * Deletes a game and everything under it, walking the layout PhasePaths describes:
     *
     *   gameSessions/{code}/rounds/{round}/stories/{storyId}
     *   gameSessions/{code}/rounds/{round}/phases/{phaseId}/{submissions|votes|views}/{id}
     *   gameSessions/{code}/rounds/{round}/encounters/x{X}y{Y}/phases/{phaseId}/{submissions|votes|views}/{id}
     *
     * Round, encounter and phase documents are never written, so listCollections() on them finds
     * which subcollections exist. Leaf documents are found with ID-only queries, all in flight at
     * once, then deleted in parallel batches; the game document goes last. This replaces
     * Firestore's recursiveDelete, which took 4-5 minutes for a full 10-player game (about one
     * document per second) and hit Cloud Run's 300s request timeout.
     *
     * A subcollection that isn't part of this layout is left in place and logged, so a new one
     * (e.g. if adventureMap moves off the game document) shows up in Cloud Logging instead of
     * silently surviving. Safe to re-run on a game whose game document is already gone.
     */
    public void deleteGame(String gameCode) {
        DocumentReference gameSessionRef = getGameRef(gameCode);
        List<ApiFuture<QuerySnapshot>> leafQueries = new ArrayList<>();

        for (CollectionReference rounds : knownCollections(gameSessionRef, Set.of(PhasePaths.ROUNDS))) {
            for (DocumentReference round : rounds.listDocuments()) {
                for (CollectionReference roundCollection : knownCollections(round, Set.of(PhasePaths.STORIES, PhasePaths.PHASES, PhasePaths.ENCOUNTERS))) {
                    switch (roundCollection.getId()) {
                        case PhasePaths.STORIES -> leafQueries.add(documentIds(roundCollection));
                        case PhasePaths.PHASES -> queryPhases(roundCollection, leafQueries);
                        case PhasePaths.ENCOUNTERS -> {
                            for (DocumentReference encounter : roundCollection.listDocuments()) {
                                for (CollectionReference phases : knownCollections(encounter, Set.of(PhasePaths.PHASES))) {
                                    queryPhases(phases, leafQueries);
                                }
                            }
                        }
                    }
                }
            }
        }

        List<DocumentReference> leafDocuments = new ArrayList<>();
        for (ApiFuture<QuerySnapshot> query : leafQueries) {
            await(query).getDocuments().forEach(document -> leafDocuments.add(document.getReference()));
        }
        deleteInBatches(leafDocuments);
        await(gameSessionRef.delete());
        System.out.println("Deleted game " + gameCode + " and " + leafDocuments.size() + " documents under it");
    }

    private void queryPhases(CollectionReference phases, List<ApiFuture<QuerySnapshot>> leafQueries) {
        for (DocumentReference phase : phases.listDocuments()) {
            for (CollectionReference phaseCollection : knownCollections(phase, Set.copyOf(PhasePaths.PHASE_COLLECTIONS))) {
                leafQueries.add(documentIds(phaseCollection));
            }
        }
    }

    // The document's subcollections that are part of the layout; any other is logged and left alone
    private static List<CollectionReference> knownCollections(DocumentReference document, Set<String> knownIds) {
        List<CollectionReference> known = new ArrayList<>();
        for (CollectionReference collection : document.listCollections()) {
            if (knownIds.contains(collection.getId())) {
                known.add(collection);
            } else {
                System.out.println("WARNING deleteGame: leaving unknown collection " + collection.getPath()
                        + " - add it to PhasePaths and GameSessionDAO.deleteGame if it belongs to the game");
            }
        }
        return known;
    }

    private static ApiFuture<QuerySnapshot> documentIds(CollectionReference collection) {
        return collection.select(FieldPath.documentId()).get();
    }

    private void deleteInBatches(List<DocumentReference> documents) {
        List<ApiFuture<List<WriteResult>>> commits = new ArrayList<>();
        for (int start = 0; start < documents.size(); start += MAX_BATCH_WRITES) {
            WriteBatch batch = db.batch();
            documents.subList(start, Math.min(start + MAX_BATCH_WRITES, documents.size())).forEach(batch::delete);
            commits.add(batch.commit());
        }
        commits.forEach(GameSessionDAO::await);
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Interrupted while deleting the game session", e);
        } catch (ExecutionException e) {
            throw new ResourceException("There was an issue deleting the game session", e);
        }
    }

    public GameSession getGameInTransaction(String gameCode, Transaction txn) {
        try {
            DocumentReference ref = getGameRef(gameCode);
            DocumentSnapshot snapshot = txn.get(ref).get();
            if (!snapshot.exists()) {
                throw new ResourceException("GameSession " + gameCode + " not found");
            }
            GameSession gameSession = snapshot.toObject(GameSession.class);
            gameSession.setStories(storyDAO.loadStoriesInTransaction(txn, gameCode, roundNumber(gameSession)));
            return gameSession;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get GameSession in transaction", e);
        }
    }

    /** Adds one story inside a transaction that read the game with getGameInTransaction. */
    public void addStoryInTransaction(GameSession gameSession, Story story, Transaction txn) {
        story.setGameCode(gameSession.getGameCode());
        storyDAO.createStoryInTransaction(txn, story, roundNumber(gameSession));
    }

    public <T> T runInTransaction(Transaction.Function<T> txnLogic) {
        try {
            return db.runTransaction(txnLogic).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transaction interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Transaction failed", e.getCause());
        }
    }

    public void initializeDungeonGrid(String gameCode, GameBoard gameBoard, List<EncounterLabel> encounterLabels) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            ApiFuture<WriteResult> result = gameSessionRef.update(
                "gameBoard", gameBoard,
                "adventureMap.encounterLabels", encounterLabels
            );
            result.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue initializing the dungeon grid", e);
        }
    }

    public void updateDungeonGrid(String gameCode, GameBoard gameBoard) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            ApiFuture<WriteResult> result = gameSessionRef.update("gameBoard", gameBoard);
            result.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the dungeon grid", e);
        }
    }

    public void updatePlayerCoordinates(String gameCode, PlayerCoordinates playerCoordinates) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            ApiFuture<WriteResult> result = gameSessionRef.update("gameBoard.playerCoordinates", playerCoordinates);
            result.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating player coordinates", e);
        }
    }
}
