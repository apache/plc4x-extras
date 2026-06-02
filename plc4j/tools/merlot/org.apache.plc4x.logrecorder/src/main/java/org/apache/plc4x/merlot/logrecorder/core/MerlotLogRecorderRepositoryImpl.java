/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.plc4x.merlot.logrecorder.core;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import org.apache.plc4x.merlot.logrecorder.api.MerlotLogRecorderRepository;
import org.apache.plc4x.merlot.logrecorder.entity.LogEntry;

@Transactional
public class MerlotLogRecorderRepositoryImpl
    implements MerlotLogRecorderRepository
{

    @PersistenceContext(unitName = "OlogPersistenceUnit")
    private EntityManager entityManager;

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(LogEntry logEntry) {
        entityManager.persist(logEntry);
        System.out.println("Saved log entry: " + logEntry);
    }

    @Override
    public void delete(LogEntry logEntry) {
        System.out.println("Deleting log entry: " + logEntry);
    }

    @Override
    public List<LogEntry> findAll() {
        return null;
    }

    @Override
    public LogEntry findById(long id) {
        return null;
    }
}
