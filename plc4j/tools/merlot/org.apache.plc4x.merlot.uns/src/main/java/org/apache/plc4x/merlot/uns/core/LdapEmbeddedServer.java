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
package org.apache.plc4x.merlot.uns.core;

import java.io.File;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.csn.Csn;
import org.apache.directory.api.ldap.model.csn.CsnFactory;
import org.apache.directory.server.ApacheDsService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;

public class LdapEmbeddedServer {
    
    private final DirectoryService ds;
    private EmbeddedADSVerTrunk ads;
    private ApacheDsService dsServer = null;
    
    public LdapEmbeddedServer(DirectoryService ds) {
        this.ds = ds;
    }
    
    public void init() throws Exception{
        try
        {
            File workDir = new File( "./data/server-ldap" );
            workDir.mkdirs();
            
            InstanceLayout il = new InstanceLayout(workDir);
            
            // Create the server
            //ads = new EmbeddedADSVerTrunk(ds, workDir );
            ApacheDsService dsServer = new ApacheDsService();
            dsServer.start(il, true);
            
            // Read an entry
//            Entry result = ads.getDirectoryService().getAdminSession().lookup( new Dn( "dc=apache,dc=org" ) );
//            Entry result  = ds.getAdminSession().lookup( new Dn( "dc=apache,dc=org" ) );
            // And print it if available
//            System.out.println( "Found entry : "  + result);
            
            // optionally we can start a server too            
            //ads.startServer();
            
        }
        catch ( Exception ex ) {
            // Ok, we have something wrong going on ...
            ex.printStackTrace();
            
            
        }
    }
    
    public void destroy() {
        try {
            dsServer.stop();
            //ads.stopServer();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
}
