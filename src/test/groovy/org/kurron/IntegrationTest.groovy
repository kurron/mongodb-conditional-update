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

    List<TestData> createData( int count = 2 ) {
        (1..count).collect {
            new TestData( id: it, fencingToken: 0 )
        }
    }

    def 'sequential processing'() {
        given: 'sequential test data'
        def data = createData()

        when: 'data is saved to the database'
        data.each {
            template.save( it )
        }

        then:
        true
    }
}
