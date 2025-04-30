package org.apache.plc4x.nifi.util;

import org.apache.nifi.expression.ExpressionLanguageScope;

public class NiFi2Compatibility {
    
    public static ExpressionLanguageScope getEnvironmentScope() {

        try {
            // NIFI 1
            return ExpressionLanguageScope.valueOf("VARIABLE_REGISTRY");
        } catch (Exception e) {

            // NIFI 2
            return ExpressionLanguageScope.valueOf("ENVIRONMENT");
        }
    }
}
