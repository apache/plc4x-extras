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
package org.apache.plc4x.merlot.archiver.api;

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
     * 
     */    
    public GPClientInstance gpClientFactory(String ThreadsId);
    
    /**
     * 
     */    
    public GPClientInstance gpClientDefaultInstance();
    
}
