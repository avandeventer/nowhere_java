package client.nowhere.helper;

import client.nowhere.exception.ResourceException;
import com.google.auth.oauth2.TokenVerifier;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends locked transitions through a Cloud Tasks queue, which calls back POST
 * /internal/transitions on this service as a normal request (so it gets full CPU; Cloud Run
 * throttles work left running after a response). Each task carries a Google-signed OIDC token for
 * the configured service account, checked by verify(), because the service itself is public.
 *
 * Enabled when nowhere.transitions.queue is set (TRANSITIONS_QUEUE on Cloud Run).
 */
@Component
@ConditionalOnExpression("!'${nowhere.transitions.queue:}'.isEmpty()")
public class CloudTasksTransitionQueue implements TransitionQueue {

    static final String PATH = "/internal/transitions";

    private final CloudTasksClient client;
    private final String queueName;
    private final String targetUrl;
    private final String serviceAccount;
    private final TokenVerifier tokenVerifier;

    public CloudTasksTransitionQueue(
            @Value("${gcp.project-id}") String projectId,
            @Value("${nowhere.transitions.location}") String location,
            @Value("${nowhere.transitions.queue}") String queue,
            @Value("${nowhere.transitions.target-url}") String targetUrl,
            @Value("${nowhere.transitions.service-account}") String serviceAccount
    ) throws IOException {
        if (targetUrl.isEmpty() || serviceAccount.isEmpty()) {
            throw new IllegalStateException("TRANSITIONS_TARGET_URL and TRANSITIONS_SERVICE_ACCOUNT must be set with TRANSITIONS_QUEUE");
        }
        this.client = CloudTasksClient.create();
        this.queueName = QueueName.of(projectId, location, queue).toString();
        this.targetUrl = targetUrl.replaceAll("/+$", "");
        this.serviceAccount = serviceAccount;
        this.tokenVerifier = TokenVerifier.newBuilder()
                .setAudience(this.targetUrl)
                .setIssuer("https://accounts.google.com")
                .build();
    }

    @Override
    public void enqueue(String gameCode, String lockId) {
        String url = targetUrl + PATH
                + "?gameCode=" + URLEncoder.encode(gameCode, StandardCharsets.UTF_8)
                + "&lockId=" + URLEncoder.encode(lockId, StandardCharsets.UTF_8);
        Task task = Task.newBuilder()
                .setHttpRequest(HttpRequest.newBuilder()
                        .setHttpMethod(HttpMethod.POST)
                        .setUrl(url)
                        .setOidcToken(OidcToken.newBuilder()
                                .setServiceAccountEmail(serviceAccount)
                                .setAudience(targetUrl)))
                .build();
        client.createTask(queueName, task);
    }

    @Override
    public void verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResourceException("Missing transition task token");
        }
        try {
            Object email = tokenVerifier.verify(authorizationHeader.substring("Bearer ".length()))
                    .getPayload().get("email");
            if (!serviceAccount.equals(email)) {
                throw new ResourceException("Transition task token is for " + email + ", not " + serviceAccount);
            }
        } catch (TokenVerifier.VerificationException e) {
            throw new ResourceException("Invalid transition task token", e);
        }
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
