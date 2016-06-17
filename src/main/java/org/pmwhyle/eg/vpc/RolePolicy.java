package org.pmwhyle.eg.vpc;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 */
public class RolePolicy {

    @JsonProperty("Version")
    public final String version = "2012-10-17";

    @JsonProperty("Statement")
    public final Statement statement = new Statement();

    private class Statement {
        @JsonProperty("Effect")
        public final String effect = "Allow";

        @JsonProperty("Principal")
        public final Principal principal = new Principal();

        @JsonProperty("Action")
        public final String action = "sts:AssumeRole";

        private class Principal {
            @JsonProperty("Service")
            public final String service = "ec2.amazonaws.com";
        }

    }
}
