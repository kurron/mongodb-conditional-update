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
        template.indexOps( TestData ).ensureIndex( new Index().on( '_id', Sort.Direction.ASC ).on( 'fencingToken', Sort.Direction.DESC ).unique( Index.Duplicates.RETAIN ).named( 'fencingToken' ) )
//        template.indexOps( TestData ).ensureIndex( new Index().on( '_id', Sort.Direction.ASC ).on( 'fencingToken', Sort.Direction.DESC ).named( 'fencingToken' ) )
    }

    List<TestData> createData( int count = 10 ) {
        (1..count).collect {
            new TestData( id: 1, fencingToken: it, currentState: it )
        }
    }

    /*
    So here is the problem, when the query fails, an upsert is performed, event when it fails
    because the fending token is the cause.  The first time through, this is fine because it it
    an insert.  The next time through, the upsert tries to kick in and you get a duplicate key
    exception.  Unfortunately, MongoDB really wants us to split the insert from the update,
    which ie exactly what we are trying to avoid.  Could we do it in two stages?
     */
    static private List constructStatement( TestData it, boolean upsert = true ) {
        def query = new Query( where('_id').is(1 ).andOperator( where('fencingToken' ).lt( it.fencingToken ) ) )
//        def query = new Query( where('_id').is(1 ) )
        def update = new Update().set( 'currentState', it.fencingToken ).push( 'touchedBy', it.fencingToken ).set( 'fencingToken', it.fencingToken )
        def options = new FindAndModifyOptions().upsert(upsert ).returnNew(true )
        [query, update, options]
    }

    private TestData loadCurrentDocument() {
        def query = new Query( where('_id' ).is(1 ) )
        template.findOne(query, TestData)
    }

    private List<TestData> saveToDatabase(List<TestData> data) {
        data.collect {
            try {
                def (Query query, Update update, FindAndModifyOptions options) = constructStatement(it)
                template.findAndModify(query, update, options, TestData)
            }
            catch (DuplicateKeyException) {
                def (Query query, Update update, FindAndModifyOptions options) = constructStatement(it, false)
                template.findAndModify(query, update, options, TestData)
            }
        }
    }

    def 'sequential processing'() {
        given: 'sequential test data'
        def data = createData()

        when: 'data is saved to the database'
        saveToDatabase(data)

        then: 'state in the database matches expectations'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.last().currentState
    }

    def 'reverse processing'() {
        given: 'reversed test data'
        def data = createData().reverse()

        when: 'data is saved to the database'
        saveToDatabase(data)

        then: 'state in the database matches expectations'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.first().currentState
    }

    def 'random processing'() {
        given: 'randomized test data'
        def data = createData()
        Collections.shuffle( data )

        when: 'data is saved to the database'
        saveToDatabase(data)

        then: 'state in the database matches expectations'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.currentState.max()
    }
}
