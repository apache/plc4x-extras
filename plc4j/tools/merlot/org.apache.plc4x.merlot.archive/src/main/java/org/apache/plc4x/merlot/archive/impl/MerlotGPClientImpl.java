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

package org.apache.plc4x.merlot.archive.impl;

import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.plc4x.merlot.archive.api.MerlotGPClient;
import org.epics.gpclient.CollectorExpression;
import org.epics.gpclient.Expression;
import org.epics.gpclient.GPClient;
import org.epics.gpclient.GPClientConfiguration;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVConfiguration;
import org.epics.gpclient.PVReaderConfiguration;
import org.epics.gpclient.ReadCollector;
import org.epics.gpclient.WriteCollector;
import org.epics.gpclient.datasource.CompositeDataSource;
import org.epics.gpclient.datasource.DataSourceProvider;
import org.epics.vtype.VType;

/**
 *
 * @author cgarcia
 */
public class MerlotGPClientImpl implements MerlotGPClient {
    
    private GPClientInstance gpClient;    
    
    
    @Override
    public void init() {
        ServiceLoader<DataSourceProvider> ldr = ServiceLoader.load(DataSourceProvider.class);
        CompositeDataSource cds = new CompositeDataSource();
        for (DataSourceProvider spiObject : ldr) {
            cds.putDataSource(spiObject.getName(), spiObject.createInstance());
        }
       
        this.gpClient = new GPClientConfiguration().defaultMaxRate(Duration.ofMillis(50))
                .notificationExecutor(org.epics.util.concurrent.Executors.localThread())
                .dataSource(cds)
                .dataProcessingThreadPool(Executors.newScheduledThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                org.epics.util.concurrent.Executors.namedPool("MerlotGPClient Worker "))).build();  
    }

    @Override
    public void destroy() {
        gpClient.getDefaultDataSource().getChannels().clear();
    }    
            
    /**
     * Reads the value of the given expression, asking for {@link VType} values.
     * 
     * @param channelName the name of the channel
     * @return the future value
     */
    @Override
    public Future<VType> readOnce(String channelName) {
        return gpClient.readOnce(channelName);
    }
    
    /**
     * Reads the value of the given expression.
     * 
     * @param <R> the read type
     * @param expression the expression to read
     * @return the future value
     */
    @Override
    public <R> Future<R> readOnce(Expression<R, ?> expression) {
        return gpClient.readOnce(expression);
    }
    
    /**
     * Reads the channel with the given name, asking for {@link VType} values.
     * 
     * @param channelName the name of the channel
     * @return the configuration options
     */
    @Override
    public PVReaderConfiguration<VType> read(String channelName) {
        return gpClient.read(channelName);
    }
    
    /**
     * Reads the given expression.
     * 
     * @param <R> the read type
     * @param expression the expression to read
     * @return the configuration options
     */
    @Override
    public <R> PVReaderConfiguration<R> read(Expression<R, ?> expression) {
        return gpClient.read(expression);
    }
    
    /**
     * Reads and writes the channel with the given name, asking for {@link VType} values.
     * 
     * @param channelName the name of the channel
     * @return the configuration options
     */
    @Override
    public PVConfiguration<VType, Object> readAndWrite(String channelName) {
        return gpClient.readAndWrite(channelName);
    }

    /**
     * Reads and writes the given expression.
     * 
     * @param <R> the read type
     * @param <W> the write type
     * @param expression the expression to read and write
     * @return the configuration options
     */
    @Override
    public <R, W> PVConfiguration<R, W> readAndWrite(Expression<R, W> expression) {
        return gpClient.readAndWrite(expression);
    }
    
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
    @Override
    public <R> ReadCollector<R, R> cacheLastValue(Class<R> readType) {
        return GPClient.cacheLastValue(readType);
    }
    
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
    @Override
    public <R> ReadCollector<R, List<R>> queueAllValues(Class<R> readType) {
        return GPClient.queueAllValues(readType);
    }

    /**
     * A write buffer for the the given type.
     * 
     * @param <W> the type to write
     * @param writeType the type to write
     * @return the caching strategy
     */
    @Override
    public <W> WriteCollector<W> writeType(Class<W> writeType) {
        return GPClient.writeType(writeType);
    }

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
    @Override
    public <R, W> Expression<R, W> channel(String channelName, ReadCollector<?, R> readCollector, WriteCollector<W> writeCollector) {
        return GPClient.channel(channelName, readCollector, writeCollector);
    }
    
    /**
     * A channel that reads the given data type with the given strategy.
     * 
     * @param <R> the type to read
     * @param channelName the name of the channel
     * @param readCollector the read buffer
     * @return a new channel expression
     */
    @Override
    public <R> Expression<R, Object> channel(String channelName, ReadCollector<?, R> readCollector) {
        return GPClient.channel(channelName, readCollector);
    }

    /**
     * A channel that reads {@link VType}s caching the latest value.
     * 
     * @param channelName the name of the channel
     * @return a new channel expression
     */
    @Override
    public Expression<VType, Object> channel(String channelName) {
        return GPClient.channel(channelName);
    }

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
    @Override
    public <R, C, W> CollectorExpression<R, C, W> collector(ReadCollector<C, R> readCollector, WriteCollector<W> writeCollector) {
        return GPClient.collector(readCollector, writeCollector);
    }

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
    @Override
    public <R, C> CollectorExpression<R, C, Object> collector(ReadCollector<C, R> readCollector) {
        return GPClient.collector(readCollector);
    }

    /**
     * An expression that allows to directly send/receive values to/from
     * PVReaders/PVWriters. This can be used for testing purpose or to integrate
     * data models that do not fit datasources or services.
     * 
     * @return a new collector expression
     */
    @Override
    public CollectorExpression<VType, VType, Object> collector() {
        return collector(cacheLastValue(VType.class));
    }

    /**
     * The default instance of the general purpose client.
     * 
     * @return the default instance
     */
    @Override
    public GPClientInstance defaultInstance() {
        return gpClient;
    }    

    
}
