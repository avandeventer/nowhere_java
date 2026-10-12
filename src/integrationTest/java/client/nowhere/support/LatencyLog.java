package client.nowhere.support;

import client.nowhere.model.GameState;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Times every HTTP call made through the RestTemplate it's registered on (see
 * FullGameScenario.logLatency), tagging each with the round/gameState the scenario was in when
 * the call was made, and prints two tables when the game ends or fails - the latency counterpart
 * to SessionSizeLog, meant to be read side by side with its size-by-phase table.
 *
 * Measures client-observed wall clock (network + Cloud Run + Firestore), from this machine to
 * the live deployment. Calls are keyed by HTTP method + path, query string dropped. Thread-safe:
 * FullGameScenario.concurrentPlayers() issues per-player calls from a thread pool.
 */
public class LatencyLog implements ClientHttpRequestInterceptor {

    /** Endpoints called out explicitly as latency concerns - always listed first, even if never called. */
    private static final List<String> HIGHLIGHTED_ENDPOINTS = List.of(
            "PUT /game/next",
            "POST /collaborativeText",
            "GET /collaborativeText/available",
            "GET /collaborativeText/voting",
            "POST /collaborativeText/votes",
            "GET /collaborativeText/outcomeType",
            "GET /collaborativeText/phaseInfo",
            "GET /collaborativeText/outcomeTypes");

    // The scenario's own main-loop polling - real clients get game state from Firestore listeners,
    // not HTTP, so this is harness overhead. Still shown in the per-endpoint table, but left out of
    // the per-phase table so it doesn't drown out calls real players actually make.
    private static final String HARNESS_POLLING_ENDPOINT = "GET /game";

    private record Call(String endpoint, int roundNumber, String gameState, long millis, int status) {}

    private final ConcurrentLinkedQueue<Call> calls = new ConcurrentLinkedQueue<>();
    private volatile int currentRoundNumber = 0;
    private volatile String currentGameState = "(setup)";

    /** Tags every call from now on with this phase, until the next setPhase. */
    public void setPhase(int roundNumber, GameState gameState) {
        this.currentRoundNumber = roundNumber;
        this.currentGameState = String.valueOf(gameState);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String endpoint = request.getMethod() + " " + request.getURI().getPath();
        int roundNumber = currentRoundNumber;
        String gameState = currentGameState;
        long start = System.nanoTime();
        int status = -1;
        try {
            ClientHttpResponse response = execution.execute(request, body);
            status = response.getStatusCode().value();
            return response;
        } finally {
            calls.add(new Call(endpoint, roundNumber, gameState, (System.nanoTime() - start) / 1_000_000, status));
        }
    }

    public void printSummary(String gameCode) {
        List<Call> snapshot = new ArrayList<>(calls);
        StringBuilder out = new StringBuilder();

        out.append("\n[LatencyLog] ===== Latency by endpoint, gameCode=").append(gameCode).append(" =====\n");
        out.append(String.format("  %-42s %6s %6s %8s %8s %8s %8s%n", "endpoint", "calls", "errors", "avg ms", "p50 ms", "p95 ms", "max ms"));
        Map<String, List<Call>> byEndpoint = groupBy(snapshot, Call::endpoint);
        Set<String> endpointOrder = new LinkedHashSet<>(HIGHLIGHTED_ENDPOINTS);
        byEndpoint.keySet().stream().sorted().forEach(endpointOrder::add);
        for (String endpoint : endpointOrder) {
            String marker = HIGHLIGHTED_ENDPOINTS.contains(endpoint) ? "* " : "  ";
            List<Call> endpointCalls = byEndpoint.getOrDefault(endpoint, List.of());
            if (endpointCalls.isEmpty()) {
                out.append(String.format("%s%-42s %6s%n", marker, endpoint, "not called"));
                continue;
            }
            out.append(marker).append(String.format("%-42s ", endpoint)).append(stats(endpointCalls)).append('\n');
        }
        out.append("  (* = highlighted endpoint; ").append(HARNESS_POLLING_ENDPOINT)
                .append(" is mostly the harness's own polling, not a real client call)\n");

        out.append("\n[LatencyLog] ===== Latency by phase and endpoint (excluding ").append(HARNESS_POLLING_ENDPOINT)
                .append("), gameCode=").append(gameCode).append(" =====\n");
        out.append(String.format("%-5s %-34s %-42s %6s %6s %8s %8s %8s %8s%n",
                "round", "gameState", "endpoint", "calls", "errors", "avg ms", "p50 ms", "p95 ms", "max ms"));
        Map<String, List<Call>> byPhase = groupBy(snapshot, call -> call.roundNumber() + "|" + call.gameState());
        for (List<Call> phaseCalls : byPhase.values()) {
            Call first = phaseCalls.get(0);
            Map<String, List<Call>> phaseByEndpoint = groupBy(phaseCalls, Call::endpoint);
            phaseByEndpoint.keySet().stream()
                    .filter(endpoint -> !HARNESS_POLLING_ENDPOINT.equals(endpoint))
                    .sorted(Comparator.comparing((String endpoint) -> !HIGHLIGHTED_ENDPOINTS.contains(endpoint))
                            .thenComparing(Comparator.naturalOrder()))
                    .forEach(endpoint -> out.append(String.format("%-5d %-34s %-42s ",
                                    first.roundNumber(), first.gameState(), endpoint))
                            .append(stats(phaseByEndpoint.get(endpoint))).append('\n'));
        }
        System.out.println(out);
    }

    /** Groups preserving first-seen order, so phases print in the order the game reached them. */
    private static Map<String, List<Call>> groupBy(List<Call> calls, java.util.function.Function<Call, String> key) {
        Map<String, List<Call>> grouped = new LinkedHashMap<>();
        for (Call call : calls) {
            grouped.computeIfAbsent(key.apply(call), k -> new ArrayList<>()).add(call);
        }
        return grouped;
    }

    private static String stats(List<Call> calls) {
        List<Long> millis = calls.stream().map(Call::millis).sorted().toList();
        long errors = calls.stream().filter(call -> call.status() < 200 || call.status() >= 400).count();
        double avg = millis.stream().mapToLong(Long::longValue).average().orElse(0);
        return String.format("%6d %6d %8.0f %8d %8d %8d",
                calls.size(), errors, avg, percentile(millis, 50), percentile(millis, 95), millis.get(millis.size() - 1));
    }

    private static long percentile(List<Long> sortedMillis, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedMillis.size()) - 1;
        return sortedMillis.get(Math.max(0, Math.min(index, sortedMillis.size() - 1)));
    }
}
