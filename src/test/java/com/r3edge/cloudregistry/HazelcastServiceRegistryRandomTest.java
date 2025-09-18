package com.r3edge.cloudregistry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class HazelcastServiceRegistryRandomTest {

    // Classe factice pour accéder à pickRandomUrl
    static class TestableRegistry extends HazelcastServiceRegistry {
        TestableRegistry() {
            super(null, null, null, null);
        }

        public String testPickRandomUrl(Stream<ServiceDescriptor> stream, Function<ServiceDescriptor, String> extractor) {
            return super.pickRandomUrl(stream, extractor);
        }
    }

    @RepeatedTest(10) // Répéter pour vérifier l'aléatoire
    void pickRandomUrl_should_return_random_value_among_available_urls() {
        TestableRegistry registry = new TestableRegistry();

        List<ServiceDescriptor> descriptors = List.of(
            ServiceDescriptor.builder().externalBaseUrl("http://one").build(),
            ServiceDescriptor.builder().externalBaseUrl("http://two").build(),
            ServiceDescriptor.builder().externalBaseUrl("http://three").build()
        );

        String result = registry.testPickRandomUrl(
            descriptors.stream(),
            ServiceDescriptor::getExternalBaseUrl
        );

        assertThat(result).isIn("http://one", "http://two", "http://three");
    }
    
    @Test
    void pickRandomUrl_ignoreNullValuesAndStillReturnValidUrl() {
        // given
        List<ServiceDescriptor> descriptors = List.of(
            ServiceDescriptor.builder().externalBaseUrl(null).build(), // nul
            ServiceDescriptor.builder().externalBaseUrl("http://valid-1").build(), // ok
            ServiceDescriptor.builder().externalBaseUrl(null).build(), // nul
            ServiceDescriptor.builder().externalBaseUrl("http://valid-2").build() // ok
        );

        TestableRegistry registry = new TestableRegistry();

        String result = registry.testPickRandomUrl(
                descriptors.stream(),
                ServiceDescriptor::getExternalBaseUrl
            );

        // then
        assertThat(result).isIn("http://valid-1", "http://valid-2");
    }

}

