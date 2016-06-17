package org.pmwhyle.eg.vpc

import spock.lang.Specification

import static org.hamcrest.Matchers.containsString

public class TestVpcInfo extends Specification {

    def 'Simple toString()'() {
        given:
          String result = new VpcInfo('my-id', '192.168.1.1', ['10.2.2.24', '10.2.2.45'])
          println result
        expect:
          result containsString('my-id')
          result containsString('10.2.2.24')
          result containsString('10.2.2.45')
          result containsString('192.168.1.1')
    }
}