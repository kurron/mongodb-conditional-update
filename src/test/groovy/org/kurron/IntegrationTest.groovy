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

    List<TestData> createData( int count = 1000 ) {
        (1..count).collect {
            new TestData( id: 1, fencingToken: it, currentState: it )
        }
    }

    /*
    So here is the problem, when the query fails, an upsert is performed. The first time through,
    this is fine because it it an insert.  The next time through, due to the fencing token, the
    upsert tries to kick in and you get a duplicate key exception.  Unfortunately, MongoDB really
    wants us to split the insert from the update, which ie exactly what we are trying to avoid.
    The solution is try first with upsert enabled.  If you fail due to a duplicate key, we know
    that the document is there and we try again with upsert disabled.  If the fencing token
    prevents the update from happening, then we are done.  It is too bad we have to make
    two trips to the database but it works and is probably the simplest solution.
     */
    static private List constructStatement( TestData it, boolean upsert = true ) {
        def query = new Query( where('_id').is(1 ).andOperator( where('fencingToken' ).lt( it.fencingToken ) ) )
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
        def data = createData( 10000 )
        Collections.shuffle( data )

        when: 'data is saved to the database'
        saveToDatabase(data)

        then: 'state in the database matches expectations'
        def inDatabase = loadCurrentDocument()
        inDatabase.currentState == data.currentState.max()
    }
}
