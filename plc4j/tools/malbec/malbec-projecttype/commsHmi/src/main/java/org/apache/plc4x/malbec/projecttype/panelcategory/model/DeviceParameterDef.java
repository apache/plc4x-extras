/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import java.util.function.Predicate;

public class DeviceParameterDef {
    private final String key;
    private final String defaultValue;
    private final boolean required;
    private final Predicate<String> validator;
    private final String errorMessage;

    public DeviceParameterDef(String key, String defaultValue, boolean required, 
                              Predicate<String> validator, String errorMessage) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.required = required;
        this.validator = validator;
        this.errorMessage = errorMessage;
    }

    public String getKey() { 
        return key; 
    }
    
    public String getDefaultValue() { 
        return defaultValue; 
    }
    
    public boolean isRequired() { 
        return required; 
    }

    public ValidationResult validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            if (required) {
                return ValidationResult.invalid("El parámetro '" + key + "' es obligatorio y no puede estar vacío.");
            }
            return ValidationResult.valid();
        }

        if (validator != null && !validator.test(value.trim())) {
            return ValidationResult.invalid("El parámetro '" + key + "' " + errorMessage);
        }

        return ValidationResult.valid();
    }
}
