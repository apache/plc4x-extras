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

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;


@CreateDS(name = "myDS",
    partitions = {
        @CreatePartition(name = "test", suffix = "dc=myorg,dc=com")
    })    
@CreateLdapServer(transports = { @CreateTransport(protocol = "LDAP", address = "localhost", port=10359)})
@ApplyLdifFiles(value = {"data/autentia-identity-repository.ldif"})
public class LdapEmbeddedServerV2 {
    
    public void init(){
        System.out.println("Inicalizo el servidor...");
    } 
    
    public void destroy() {
        System.out.println("Destruye el servidor...");        
    }


    
}
