package cafe.snails.ecomagents.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelPropertiesTest {
    @Test
    void runtimeDefaultsAreStable() {
        var properties = new ModelRuntimeProperties();
        assertEquals(60, properties.getStreamTimeout());
        assertEquals(30, properties.getConnectionTimeout());
        assertEquals(55, properties.getReadTimeout());
    }

    @Test
    void ragDefaultsAreStable() {
        var properties = new RagProperties();
        assertEquals(5, properties.getSearchLimit());
        assertEquals(0.15, properties.getSimilarityThreshold());
        assertEquals(8, properties.getRetrievalTimeout());
        assertEquals(16000, properties.getMaxContextChars());
    }
}
