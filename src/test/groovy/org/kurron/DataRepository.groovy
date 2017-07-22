package org.kurron

import org.springframework.data.repository.CrudRepository

/**
 * Spring managed repository.
 */
interface DataRepository extends CrudRepository<TestData, Integer> { }


