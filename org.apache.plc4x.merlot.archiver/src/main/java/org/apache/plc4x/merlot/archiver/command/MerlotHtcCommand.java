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
package org.apache.plc4x.merlot.archiver.command;

import java.time.Instant;
import java.util.List;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.apache.plc4x.merlot.archiver.impl.MerlotHtcIoTDBImpl;
import org.apache.plc4x.merlot.archiver.impl.MerlotHtcRTImpl;
import org.epics.vtype.Scalar;
import org.epics.vtype.Time;
import org.epics.vtype.VType;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

@Service
@Command(scope = "plc4x", name = "htc", description = "Historical collector admin.")
public class MerlotHtcCommand implements Action {

    @Reference
    BundleContext bc;

    @Reference
    volatile List<MerlotHtc> htcs;

    @Option(name = "-l", aliases = "--list", description = "List collector PVs.", required = false, multiValued = false)
    String strHtc;

    @Option(name = "-p", aliases = "--pv", description = "Displays the stored values ​​of the process variable.", required = false, multiValued = false)
    String strPV;

    @Option(name = "-a", aliases = "--add", description = "The designated PV tags must be incorporated into the collector.", required = false, multiValued = false)
    Boolean blnAdd;

    @Option(name = "-f", aliases = "--from", description = "Start date for data query.", required = false, multiValued = false)
    String from;

    @Option(name = "-t", aliases = "--to", description = "End date for data consultation.", required = false, multiValued = false)
    String to;

    @Option(name = "-r", aliases = "--remove", description = "The designated PV tags must be remove from the collector.", required = false, multiValued = false)
    String strRemovePV;

    @Argument(index = 0, name = "htc", description = "The Collector to which the PV tag will be assigned.", required = false, multiValued = false)
    String strMainHtc;

    @Argument(index = 1, name = "rate", description = "Maximum rate at which a tag change must be acquired.", required = false, multiValued = false)
    String strMaxRate;

    @Argument(index = 2, name = "pvs", description = "List of PVS tags to htc.", required = false, multiValued = true)
    List<String> strPVs;

    
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotHtcCommand.class);
    @Override
    public Object execute() throws Exception {
      
        if ((null == strHtc) && (null == strMainHtc) ){
            ListCollectorsServices(null);
        } else  if ((null != strHtc) && (null == strMainHtc) && (null == strPV)) {
            ListCollectorsServices(strHtc);
        } else  if ((null != strHtc) && (null == strMainHtc) && (null != strPV)){
                ListHistoricalValues(strPV, from, to);
         } else  if ((blnAdd) && (null != strMainHtc) && (null != strMaxRate) && (null != strPVs)){
            addPV(strMainHtc, strMaxRate, strPVs);
         } else if ((null != strRemovePV) && (null != strMainHtc)) {
             removePV(strMainHtc, strRemovePV);
         }

        
        return null;
    }

    private void ListCollectorsServices(String id) {
        if (null == id) {
            ShellTable table = new ShellTable();
            table.column("Uid");
            table.column("Key");
            htcs.forEach(htc -> {
                table.addRow().addContent(htc.getID(), htc.getPVs().size());
            });
            table.print(System.out);

        } else {
            htcs.forEach(h -> {
                if (h.getID().equalsIgnoreCase(id)) {
                    int count = 1;
                    ShellTable table = new ShellTable();
                    table.column("Item");
                    table.column("PV");
                    h.getPVs().forEach(pc -> {
                        table.addRow().addContent(count, pc);
                    });
                    table.print(System.out);
                }
            });

        }
    }

    private void ListHistoricalValues(String pv, String init, String end) {
        int[] counter = new int[1];
        htcs.forEach(h -> {
            if (h.getID().equalsIgnoreCase(strHtc)) {
                String strInit = (null == init) ? Instant.MIN.toString() : init;
                String strEnd = (null == end) ? Instant.MAX.toString() : end;

                var pvs = h.getPVs(pv, strInit, strEnd);
                ShellTable table = new ShellTable();
                table.column("Date");
                table.column("Value");
                counter[0] = 1;
                pvs.forEach((p) -> {
                    table.addRow().addContent(counter[0], p.toString());
                    counter[0]++;
                });
                table.print(System.out);
            }
        });
    }

    private void addPV(String pv, String maxRate, List<String> listPVs) {
        htcs.forEach(h -> {
            if (h.getID().equalsIgnoreCase(strMainHtc)) {
                listPVs.forEach(s -> {
                    h.addPV(s, Double.MAX_VALUE);
                });
            }
        });
    }

    private void removePV(String htc, String strpv) {
        htcs.forEach(h -> {
            if (h.getID().equalsIgnoreCase(htc)) {
                h.removePV(strpv);
            }
        });
    }

}
