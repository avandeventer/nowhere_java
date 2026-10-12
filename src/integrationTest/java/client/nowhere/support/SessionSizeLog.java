package client.nowhere.support;

import client.nowhere.model.GameState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks how big the GameSession gets as a game progresses, for comparing runs before/after
 * changes aimed at Firestore's 1 MiB per-document limit (players, stories, collaborativeTextPhases,
 * etc. all live on the one gameSessions/{gameCode} document - see GameFlowClient.deleteGame).
 *
 * Measured from GET /game's JSON body, which is a close proxy for - not an exact match of - the
 * stored Firestore document size: Firestore counts field names/values in its own encoding, and
 * the REST response can include or omit fields the stored document doesn't (e.g. @JsonIgnore'd
 * getters, derived getters like Player.getDisplayName). Good for relative growth and for spotting
 * which field dominates; don't treat the absolute number as the byte count Firestore enforces.
 * In practice it UNDER-counts: game EZFWSS failed Firestore's write at 1,050,513 bytes while this
 * log read ~949 KB (~92.6%) - so expect the real limit to hit at roughly 90% here.
 *
 * One sample is taken on entry to every phase (each new gameState/roundNumber pair), recording
 * the total size, each top-level field's size, and how long the previous phase took to get
 * through (wall clock, including all of the scenario's HTTP calls for that phase).
 */
public class SessionSizeLog {

    private static final long FIRESTORE_DOCUMENT_LIMIT_BYTES = 1_048_576;
    private static final List<String> TRACKED_FIELDS = List.of(
            "stories", "players", "collaborativeTextPhases", "adventureMap", "gameBoard", "endings", "rituals");

    private record Sample(int roundNumber, String gameState, long totalBytes, Map<String, Long> fieldBytes,
                          long startedAtMillis, Long phaseMillis) {}

    private final GameFlowClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Sample> samples = new ArrayList<>();
    private GameState lastGameState;
    private int lastRoundNumber = -1;

    public SessionSizeLog(GameFlowClient client) {
        this.client = client;
    }

    /** Takes a sample if this is the first observation of a new gameState/roundNumber pair. */
    public void record(String gameCode, GameState gameState, int roundNumber) {
        if (gameState == lastGameState && roundNumber == lastRoundNumber) {
            return;
        }
        lastGameState = gameState;
        lastRoundNumber = roundNumber;
        takeSample(gameCode, String.valueOf(gameState), roundNumber);
        Sample sample = samples.get(samples.size() - 1);
        System.out.println("[SessionSizeLog] gameCode=" + gameCode + " round=" + roundNumber + " gameState=" + gameState
                + " size=" + formatKb(sample.totalBytes()) + " (" + percentOfLimit(sample.totalBytes()) + " of 1 MiB)"
                + " fields=" + formatFields(sample.fieldBytes()));
    }

    /**
     * Prints one row per sampled phase. Takes one last sample first (labelled "(end)") so a failed
     * run shows the size at the point of failure, not just at the start of the phase that broke -
     * if that final GET itself fails (e.g. the document is already unreadable), the table is
     * printed without it.
     */
    public void printSummary(String gameCode) {
        try {
            takeSample(gameCode, "(end)", lastRoundNumber);
        } catch (RuntimeException e) {
            System.out.println("[SessionSizeLog] final sample failed: " + e.getMessage());
        }

        StringBuilder table = new StringBuilder();
        table.append("\n[SessionSizeLog] ===== GameSession size by phase, gameCode=").append(gameCode).append(" =====\n");
        table.append(String.format("%-5s %-34s %10s %8s %10s", "round", "gameState", "phase ms", "% limit", "total"));
        for (String field : TRACKED_FIELDS) {
            table.append(String.format(" %12s", abbreviate(field)));
        }
        table.append('\n');

        Sample peak = null;
        for (Sample sample : samples) {
            if (peak == null || sample.totalBytes() > peak.totalBytes()) {
                peak = sample;
            }
            table.append(String.format("%-5d %-34s %10s %8s %10s",
                    sample.roundNumber(), sample.gameState(),
                    sample.phaseMillis() == null ? "-" : sample.phaseMillis().toString(),
                    percentOfLimit(sample.totalBytes()), formatKb(sample.totalBytes())));
            for (String field : TRACKED_FIELDS) {
                Long bytes = sample.fieldBytes().get(field);
                table.append(String.format(" %12s", bytes == null ? "-" : formatKb(bytes)));
            }
            table.append('\n');
        }
        if (peak != null) {
            table.append("[SessionSizeLog] peak ").append(formatKb(peak.totalBytes()))
                    .append(" (").append(percentOfLimit(peak.totalBytes())).append(" of 1 MiB) at round ")
                    .append(peak.roundNumber()).append(' ').append(peak.gameState()).append('\n');
        }
        System.out.println(table);
    }

    private void takeSample(String gameCode, String label, int roundNumber) {
        long now = System.currentTimeMillis();
        String json = client.getGameJson(gameCode);
        long totalBytes = json == null ? 0 : json.getBytes(StandardCharsets.UTF_8).length;
        Map<String, Long> fieldBytes = new LinkedHashMap<>();
        if (json != null) {
            try {
                JsonNode root = objectMapper.readTree(json);
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    fieldBytes.put(field.getKey(), (long) field.getValue().toString().getBytes(StandardCharsets.UTF_8).length);
                }
            } catch (Exception e) {
                System.out.println("[SessionSizeLog] could not parse GET /game body for per-field sizes: " + e.getMessage());
            }
        }

        if (!samples.isEmpty()) {
            Sample previous = samples.remove(samples.size() - 1);
            samples.add(new Sample(previous.roundNumber(), previous.gameState(), previous.totalBytes(),
                    previous.fieldBytes(), previous.startedAtMillis(), now - previous.startedAtMillis()));
        }
        samples.add(new Sample(roundNumber, label, totalBytes, fieldBytes, now, null));
    }

    private static String formatFields(Map<String, Long> fieldBytes) {
        StringBuilder out = new StringBuilder("{");
        for (String field : TRACKED_FIELDS) {
            Long bytes = fieldBytes.get(field);
            if (bytes != null) {
                if (out.length() > 1) {
                    out.append(", ");
                }
                out.append(field).append('=').append(formatKb(bytes));
            }
        }
        return out.append('}').toString();
    }

    private static String abbreviate(String field) {
        return field.equals("collaborativeTextPhases") ? "collabPhases" : field;
    }

    private static String formatKb(long bytes) {
        return String.format("%.1fKB", bytes / 1024.0);
    }

    private static String percentOfLimit(long bytes) {
        return String.format("%.1f%%", 100.0 * bytes / FIRESTORE_DOCUMENT_LIMIT_BYTES);
    }
}
