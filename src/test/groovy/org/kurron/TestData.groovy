package org.kurron

import groovy.transform.Canonical
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

/**
 * Data entity to be stored in the database.
 */
@Document
@Canonical
class TestData {

    @Id
    int id

    @Field( 'fencingToken' )
    int fencingToken

    @Field( 'currentState' )
    int currentState

    @Field( 'touchedBy' )
    List<Integer> touchedBy = []
}
