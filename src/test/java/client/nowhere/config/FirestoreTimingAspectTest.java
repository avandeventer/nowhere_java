package client.nowhere.config;

import client.nowhere.dao.FeatureFlagDAO;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestFirestoreConfig.class)
@ActiveProfiles("test")
class FirestoreTimingAspectTest {

    @Autowired
    private FeatureFlagDAO featureFlagDAO;

    @Autowired
    private Firestore firestore;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void tearDown() {
        Mockito.reset(firestore);
        meterRegistry.clear();
    }

    @Test
    void recordsSuccessfulDaoCall() {
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference document = mock(DocumentReference.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(firestore.collection(anyString())).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(document);
        when(document.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(false);

        featureFlagDAO.getFlagValue("anyFlag");

        Timer timer = meterRegistry.find("firestore.dao")
                .tags("dao", "FeatureFlagDAO", "method", "getFlagValue", "outcome", "success")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void recordsFailedDaoCallAndRethrows() {
        when(firestore.collection(anyString())).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> featureFlagDAO.getFlagValue("anyFlag"));

        Timer timer = meterRegistry.find("firestore.dao")
                .tags("dao", "FeatureFlagDAO", "method", "getFlagValue", "outcome", "error")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }
}
