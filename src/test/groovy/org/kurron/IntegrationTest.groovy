package org.kurron

import spock.lang.Specification

/**
 * Integration test to see how MongoDB conditional updates behave.
 */
class IntegrationTest  extends Specification {

    def 'sequential processing'() {
        given:
        'foo'

        when:
        'bar'

        then:
        false
    }
}
