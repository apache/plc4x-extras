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
package org.apache.plc4x.merlot.archive.api;

import java.util.List;
import java.util.concurrent.Future;
import org.epics.gpclient.CollectorExpression;
import org.epics.gpclient.Expression;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVConfiguration;
import org.epics.gpclient.PVReaderConfiguration;
import org.epics.gpclient.ReadCollector;
import org.epics.gpclient.WriteCollector;
import org.epics.vtype.VType;

/**
* This is an implementation of GPClient that solves the import 
* of SPI services of the DataSourceProvider type during Merlot restart.
* 
* TODO: Evaluate the original GPClient implementation to 
* solve the service loading problem.
*/
public interface MerlotGPClient {
    
    /**
     * DataSources are created from the DataSourceProvider services 
     * available in the CLASSPATH.
     */
    public void init();
    
    /**
     * 
     */
    public void destroy();    
        
    /**
     * Reads the value of the given expression, asking for {@link VType} values.
     * 
     * @param channelName the name of the channel
     * @return the future value
     */
    public Future<VType> readOnce(String channelName);
    
    /**
     * Reads the value of the given expression.
     * 
     * @param <R> the read type
     * @param expression the expression to read
     * @return the future value
     */
    public <R> Future<R> readOnce(Expression<R, ?> expression);
    
    /**
     * Reads the channel with the given name, asking for {@link VType} values.
     * 
     * @param channelName the name of the channel
     * @return the configuration options
     */
    public PVReaderConfiguration<VType> read(String channelName);
    
    /**
     * Reads the given expression.
     * 
     * @param <R> the read type
     * @param expression the expression to read
     * @return the configuration options
     */
    public <R> PVReaderConfiguration<R> read(Expression<R, ?> expression);
    
    /**
     * Reads and writes the channel with the given name, asking for {@link VType} values.
     * 
     * @param channelName the name of the channel
     * @return the configuration options
     */
    public PVConfiguration<VType, Object> readAndWrite(String channelName);

    /**
     * Reads and writes the given expression.
     * 
     * @param <R> the read type
     * @param <W> the write type
     * @param expression the expression to read and write
     * @return the configuration options
     */
    public <R, W> PVConfiguration<R, W> readAndWrite(Expression<R, W> expression);
    
    /**
     * Keep only the latest value from the channel.
     * <p>
     * In case of data bursts (i.e. data coming in at rate faster than the
     * reader can handle) this strategy will skip the notification in between,
     * but always notify on the last value.
     * 
     * @param <R> the type to read
     * @param readType the type to read
     * @return the caching strategy
     */
    public <R> ReadCollector<R, R> cacheLastValue(Class<R> readType);
    
    /**
     * Return all the values queued from the last update.
     * <p>
     * In case of data bursts (i.e. data coming in at rate faster than the
     * reader can handle) this strategy will combine the notifications and
     * return all the values.
     * 
     * @param <R> the type to read
     * @param readType the type to read
     * @return the caching strategy
     */
    public <R> ReadCollector<R, List<R>> queueAllValues(Class<R> readType);

    /**
     * A write buffer for the the given type.
     * 
     * @param <W> the type to write
     * @param writeType the type to write
     * @return the caching strategy
     */
    public <W> WriteCollector<W> writeType(Class<W> writeType);

    /**
     * A channel that reads and writes the given data types with the given strategy.
     * 
     * @param <R> the type to read
     * @param <W> the type to write
     * @param channelName the name of the channel
     * @param readCollector the read buffer
     * @param writeCollector the write buffer
     * @return a new channel expression
     */
    public <R, W> Expression<R, W> channel(String channelName, ReadCollector<?, R> readCollector, WriteCollector<W> writeCollector);
    
    /**
     * A channel that reads the given data type with the given strategy.
     * 
     * @param <R> the type to read
     * @param channelName the name of the channel
     * @param readCollector the read buffer
     * @return a new channel expression
     */
    public <R> Expression<R, Object> channel(String channelName, ReadCollector<?, R> readCollector);

    /**
     * A channel that reads {@link VType}s caching the latest value.
     * 
     * @param channelName the name of the channel
     * @return a new channel expression
     */
    public Expression<VType, Object> channel(String channelName);

    /**
     * An expression that allows to directly send/receive values to/from
     * PVReaders/PVWriters. This can be used for testing purpose or to integrate
     * data models that do not fit datasources or services.
     * 
     * @param <R> the type to read
     * @param <C> the type to collect
     * @param <W> the type to write
     * @param readCollector the read buffer
     * @param writeCollector the write buffer
     * @return a new collector expression
     */
    public <R, C, W> CollectorExpression<R, C, W> collector(ReadCollector<C, R> readCollector, WriteCollector<W> writeCollector);

    /**
     * An expression that allows to directly send/receive values to/from
     * PVReaders/PVWriters. This can be used for testing purpose or to integrate
     * data models that do not fit datasources or services.
     * 
     * @param <R> the type to read
     * @param <C> the type to collect
     * @param readCollector the read buffer
     * @return a new collector expression
     */
    public <R, C> CollectorExpression<R, C, Object> collector(ReadCollector<C, R> readCollector);

    /**
     * An expression that allows to directly send/receive values to/from
     * PVReaders/PVWriters. This can be used for testing purpose or to integrate
     * data models that do not fit datasources or services.
     * 
     * @return a new collector expression
     */
    public CollectorExpression<VType, VType, Object> collector();

    /**
     * The default instance of the general purpose client.
     * 
     * @return the default instance
     */
    public GPClientInstance defaultInstance();
    
}
