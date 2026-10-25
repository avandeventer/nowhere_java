package client.nowhere.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Times every public DAO method so Firestore round trips show up separately from
 * total request latency (http.server.requests). Published as the "firestore.dao" timer,
 * tagged by DAO class and method. Calls from one DAO method to another in the same class
 * are not proxied and so are counted inside the outer method's time.
 *
 * When tracing is enabled, each call is also a span named "<DAO>.<method>", nested under
 * the HTTP request span, so a trace shows which Firestore calls made a request slow.
 */
@Aspect
@Component
public class FirestoreTimingAspect {

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public FirestoreTimingAspect(MeterRegistry meterRegistry, ObjectProvider<Tracer> tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
    }

    @Around("within(client.nowhere.dao..*) && execution(public * *(..))")
    public Object timeDaoCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String dao = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String method = joinPoint.getSignature().getName();

        Timer.Sample sample = Timer.start(meterRegistry);
        Span span = tracer.nextSpan().name(dao + "." + method).tag("db.system", "firestore").start();
        String outcome = "success";
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            outcome = "error";
            span.error(throwable);
            throw throwable;
        } finally {
            sample.stop(Timer.builder("firestore.dao")
                    .description("Latency of DAO methods backed by Firestore")
                    .tag("dao", dao)
                    .tag("method", method)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
            span.end();
        }
    }
}
