/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.api;

/**
 *
 * @author Daniel
 * Interface for load and save plant models.
 *
 */
public interface S88Repository {
    /**
     * Loads from any source and maps to Malbec S88 in-memory model.
     * @return model for malbec.
     */
    S88PlantModel loadPlant();

    /**
     * Exports from malbec model to any model/format required.
     * @param model Malbec model
     */
    void savePlant(S88PlantModel model);
}
