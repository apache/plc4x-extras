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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.ldap.LdapContext;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.factory.ServerAnnotationProcessor;
import org.apache.directory.server.ldap.LdapServer;


@CreateDS(name = "MethodDSWithPartitionAndServer",        
        partitions =
            {
                @CreatePartition(
                    name = "example",
                    suffix = "dc=example,dc=com",
                    contextEntry = @ContextEntry(
                        entryLdif =
                        "dn: dc=example,dc=com\n" +
                            "dc: example\n" +
                            "objectClass: top\n" +
                            "objectClass: domain\n\n"),
                    indexes =
                        {
                            @CreateIndex(attribute = "objectClass"),
                            @CreateIndex(attribute = "dc"),
                            @CreateIndex(attribute = "ou")
                    })
        })    
@CreateLdapServer(transports = { @CreateTransport(protocol = "LDAP", address = "localhost", port=10359)})
//@ApplyLdifFiles(value = {"./data/server-ldap/autentia-identity-repository.ldif"})
public class LdapEmbeddedServerV2 {
    LdapServer ldapServer =  null;
    DirectoryService service = null; 
    LdapContext ctx = null;
    
    public void init(){

        try {
            service = DSAnnotationProcessor.getDirectoryService();
            
            Set<String> expectedNames = new HashSet<String>();

            expectedNames.add( "example" );
            expectedNames.add( "schema" );            
            
            ldapServer = ServerAnnotationProcessor.getLdapServer(service);  
                       
            
        } catch (Exception ex) {
            Logger.getLogger(LdapEmbeddedServerV2.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        System.out.println("Inicalizo el servidor...");
    } 
    
    public void destroy() throws LdapException {
        System.out.println("Destruye el servidor...");  
        ldapServer.stop();
        service.shutdown();        
    }


    
}
