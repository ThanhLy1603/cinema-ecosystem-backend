package com.cinema.modules.media.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudinarySignerTest {

    @Test
    void matchesCloudinaryDocumentationExampleWithTimestampOnly() {
        assertEquals("a21ad0f63beb4de2e5575204b79ab90bffb02c10", CloudinarySigner.sign(Map.of("timestamp", "1315060510"), "abcd"));
    }

    @Test
    void matchesCloudinaryDocumentationExampleWithSortedParameters() {
        String signature = CloudinarySigner.sign(
                Map.of(
                        "timestamp", "1315060510",
                        "public_id", "sample_image",
                        "eager", "w_400,h_300,c_pad|w_260,h_200,c_crop"),
                "abcd");

        assertEquals("bfd09f95f331f558cbd1320e67aa8d488770583e", signature);
    }

    @Test
    void ignoresEmptyValuesAndParameterOrder() {
        String withEmptyValue = CloudinarySigner.sign(Map.of("b", "2", "a", "1", "c", ""), "secret");
        String withoutEmptyValue = CloudinarySigner.sign(Map.of("a", "1", "b", "2"), "secret");

        assertEquals(withoutEmptyValue, withEmptyValue);
    }
}
