package org.kurron

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import spock.lang.Specification

import static org.springframework.data.mongodb.core.query.Criteria.where

/**
 * Integration test to see how MongoDB conditional updates behave.
 */
@DataMongoTest( excludeAutoConfiguration = EmbeddedMongoAutoConfiguration )
class IntegrationTest  extends Specification {

    @Autowired
    private MongoTemplate template

    @Autowired
    private DataRepository repository

    @TestConfiguration
    static class Configuration {

    }

    def setup() {
        assert template
        assert repository
        template.dropCollection( TestData )
        template.createCollection( TestData )
        template.indexOps( TestData ).dropAllIndexes()
        template.indexOps( TestData ).ensureIndex( new Index().on( '_id', Sort.Direction.ASC ).on( 'fencingToken', Sort.Direction.ASC ).unique( Index.Duplicates.RETAIN ).named( 'fencingToken' ) )
    }

    List<TestData> createData( int count = 10 ) {
        (1..count).collect {
            new TestData( id: 1, fencingToken: it, currentState: it )
        }
    }

    def 'sequential processing'() {
        given: 'sequential test data'
        def data = createData()

        when: 'data is saved to the database'
        def updates = data.collect {
            def query = new Query( where( '_id' ).is( 1 ).andOperator( where( 'fencingToken' ).lt( it.fencingToken ) ) )
            def update = new Update().set( 'currentState', it.fencingToken ).push( 'touchedBy', it.fencingToken ).inc( 'fencingToken', 1 )
            def options = new FindAndModifyOptions().upsert( true ).returnNew( true )
            template.findAndModify( query, update, options, TestData )
        }

        then: 'state in the database matches expectations'
        updates.currentState.max() == data.currentState.max()
    }
}
