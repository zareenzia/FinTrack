package org.example.finzin.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockEmbeddingClientTest {

    private final MockEmbeddingClient client = new MockEmbeddingClient();

    @Test
    void alwaysReturns1536Dimensions() {
        assertEquals(1536, client.embed("hello world").length);
        assertEquals(1536, client.embed("").length);
        assertEquals(1536, client.embed(null).length);
    }

    @Test
    void sameInputProducesSameEmbedding() {
        float[] first = client.embed("grocery shopping at the supermarket");
        float[] second = client.embed("grocery shopping at the supermarket");
        assertArrayEquals(first, second);
    }

    @Test
    void differentInputProducesDifferentEmbedding() {
        float[] a = client.embed("grocery shopping");
        float[] b = client.embed("car maintenance appointment");
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    void nullAndEmptyInputAreTreatedAsTheSameText() {
        assertArrayEquals(client.embed(null), client.embed(""));
    }

    @Test
    void valuesFallWithinExpectedRange() {
        float[] vector = client.embed("boundary check");
        for (float v : vector) {
            assertTrue(v >= -1f && v < 1f, "value out of expected [-1,1) range: " + v);
        }
    }
}
