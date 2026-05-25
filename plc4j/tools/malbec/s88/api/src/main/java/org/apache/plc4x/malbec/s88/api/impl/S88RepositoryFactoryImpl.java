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
package org.apache.plc4x.malbec.s88.api.impl;

/**
 *
 * @author Starblend
 */
import java.util.ServiceLoader;
import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88RepositoryFactory;
import org.apache.plc4x.malbec.s88.api.S88RepositoryProvider;
import org.apache.plc4x.malbec.s88.api.S88Storage;

/**
 * Agnostic implementation of S88RepositoryFactory using ServiceLoader.
 */
public class S88RepositoryFactoryImpl implements S88RepositoryFactory {

    @Override
    public S88Repository createRepository(String format, S88Storage storage) {
        ServiceLoader<S88RepositoryProvider> loader = ServiceLoader.load(S88RepositoryProvider.class);
        for (S88RepositoryProvider provider : loader) {
            if (provider.accepts(format)) {
                return provider.createRepository(storage);
            }
        }
        throw new IllegalArgumentException("No S88RepositoryProvider found for format: " + format);
    }
}
