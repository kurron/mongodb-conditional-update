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

import java.time.Duration

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
        template.indexOps( TestData ).ensureIndex( new Index().on( '_id', Sort.Direction.ASC ).on( 'fencingToken', Sort.Direction.DESC ).unique( Index.Duplicates.RETAIN ).named( 'fencingToken' ) )
    }

    List<TestData> createData( int count = 1000 ) {
        (1..count).collect {
            new TestData( id: 1, fencingToken: it, currentState: it )
        }
    }

    private TestData loadCurrentDocument() {
        def query = new Query( where('_id' ).is(1 ) )
        TestData found = template.findOne(query, TestData)
        found
    }

    private String saveToDatabase(List<TestData> data) {
        Map<Integer,Boolean> idToExists = [:]
        long start = System.currentTimeMillis()
        data.each {
            def key = it.id
            // The use of documentExists.getOrDefault( key, isDocumentInDatabase( key ) ) did not work as I expected
            def documentExists = idToExists.containsKey( key ) ? idToExists.get( key ) : isDocumentInDatabase( key )
            boolean requireUpsert = !documentExists
            def query = new Query( where('_id').is(1 ).andOperator( where('fencingToken' ).lt( it.fencingToken ) ) )
            def update = new Update().set( 'currentState', it.fencingToken ).push( 'touchedBy', it.fencingToken ).set( 'fencingToken', it.fencingToken )
            def options = new FindAndModifyOptions().upsert( requireUpsert ).returnNew(true )
            template.findAndModify(query, update, options, TestData)
            idToExists.put( key, true )
        }
        long stop = System.currentTimeMillis()
        long duration = stop - start
        Duration.ofMillis( duration ) as String
    }

    private boolean isDocumentInDatabase( int id ) {
        println 'isDocumentInDatabase!'
        null != template.findById( id, TestData )
    }

    // make into data driven test

    def 'random processing'() {
        given: 'randomized test data'
        def size = 10000
        List<TestData> data = createData( size )
        Collections.shuffle( data )

        when: 'data is saved to the database'
        def duration = saveToDatabase( data )
        println "${size} events took ${duration} to process"

        then: 'state in the database matches expectations'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.currentState.max()
    }

    def 'sequential processing'() {
        given: 'sequential test data'
        def size = 10000
        def data = createData( size )

        when: 'data is saved to the database'
        def duration = saveToDatabase( data )
        println "${size} events took ${duration} to process"

        then: 'the database contains the expected state'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.last().currentState

        and: 'the document was touched once for each event'
        inDatabase.touchedBy.size() == size
    }

    def 'reverse processing'() {
        given: 'reversed test data'
        def size = 10000
        def data = createData( size ).reverse()

        when: 'data is saved to the database'
        def duration = saveToDatabase( data )
        println "${size} events took ${duration} to process"

        then: 'state in the database matches expectations'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.first().currentState

        and: 'the document was touched only by the first event'
        inDatabase.touchedBy.size() == 1
    }

}
