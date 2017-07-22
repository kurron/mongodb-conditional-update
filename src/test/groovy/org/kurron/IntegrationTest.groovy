package org.kurron

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import spock.lang.Ignore
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
//        template.indexOps( TestData ).ensureIndex( new Index().on( '_id', Sort.Direction.ASC ).on( 'fencingToken', Sort.Direction.DESC ).unique( Index.Duplicates.RETAIN ).named( 'fencingToken' ) )
//        template.indexOps( TestData ).ensureIndex( new Index().on( '_id', Sort.Direction.ASC ).on( 'fencingToken', Sort.Direction.DESC ).named( 'fencingToken' ) )
    }

    List<TestData> createData( int count = 10 ) {
        (1..count).collect {
            new TestData( id: 1, fencingToken: it, currentState: it )
        }
    }

    static private List constructStatement( TestData it ) {
        def query = new Query( where('_id').is(1 ).andOperator( where('fencingToken' ).lt( it.fencingToken ) ) )
//        def query = new Query( where('_id').is(1 ) )
        def update = new Update().set( 'currentState', it.fencingToken ).push( 'touchedBy', it.fencingToken ).setOnInsert( 'fencingToken', it.fencingToken )
        def options = new FindAndModifyOptions().upsert(true ).returnNew(true )
        [query, update, options]
    }

    def 'sequential processing'() {
        given: 'sequential test data'
        def data = createData()

        when: 'data is saved to the database'
        def updates = data.collect {
            def (Query query, Update update, FindAndModifyOptions options) = constructStatement( it )
            template.findAndModify( query, update, options, TestData )
        }

        then: 'state in the database matches expectations'
        def query = new Query( where('_id').is(1 ) )
        def inDatabase = template.findOne( query, TestData )
        inDatabase.currentState == data.last().currentState
    }

    def 'reverse processing'() {
        given: 'reversed test data'
        def data = createData().reverse()

        when: 'data is saved to the database'
        def updates = data.collect {
            def (Query query, Update update, FindAndModifyOptions options) = constructStatement( it )
            template.findAndModify( query, update, options, TestData )
        }

        then: 'state in the database matches expectations'
        def query = new Query( where('_id').is(1 ) )
        def inDatabase = template.findOne( query, TestData )
        inDatabase.currentState == data.first().currentState
    }

    @Ignore
    def 'random processing'() {
        given: 'randomized test data'
        def data = createData()
        Collections.shuffle( data )

        when: 'data is saved to the database'
        def updates = data.collect {
            def (Query query, Update update, FindAndModifyOptions options) = constructStatement( it )
            template.findAndModify( query, update, options, TestData )
        }

        then: 'state in the database matches expectations'
        def query = new Query( where('_id').is(1 ) )
        def inDatabase = template.findOne( query, TestData )
        inDatabase.currentState == data.currentState.max()
    }
}
