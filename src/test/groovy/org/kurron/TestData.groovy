package org.kurron

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Data entity to be stored in the database.
 */
@Document
class TestData {

    @Id
    int id

    int fencingToken

    List<Integer> touchedBy = []
}
