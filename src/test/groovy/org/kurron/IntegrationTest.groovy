package org.kurron

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.mongodb.core.MongoTemplate
import spock.lang.Specification

/**
 * Integration test to see how MongoDB conditional updates behave.
 */
@DataMongoTest( excludeAutoConfiguration = EmbeddedMongoAutoConfiguration )
class IntegrationTest  extends Specification {

    @Autowired
    private MongoTemplate template

    @TestConfiguration
    static class Configuration {

    }

    def setup() {
        assert template
        template.dropCollection( TestData )
    }

    def 'sequential processing'() {
        given:
        'foo'

        when:
        'bar'

        then:
        true
    }
}
