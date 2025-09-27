package client.nowhere.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.google.cloud.firestore.Firestore;

@TestConfiguration
@Profile("test")
public class TestFirestoreConfig {

    @Bean
    @Primary
    public Firestore firestore() {
        return Mockito.mock(Firestore.class);
    }
}
