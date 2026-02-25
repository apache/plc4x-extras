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
package org.apache.plc4x.malbec.core.scheduler.core;

import java.util.Properties;
import org.apache.plc4x.malbec.core.scheduler.api.Scheduler;
import org.openide.modules.ModuleInstall;
import org.openide.util.Lookup;

public class Activator extends ModuleInstall {

    private QuartzScheduler scheduler;
    private WhiteboardHandler whiteboardHandler;

    @Override
    public void restored() {     
        System.out.println("ACTIVO");
        Properties properties = new Properties();
        Scheduler scheduler = Lookup.getDefault().lookup(Scheduler.class);
        whiteboardHandler = new WhiteboardHandler(scheduler);
        
//        SchedulerMBeanImpl mBean = new SchedulerMBeanImpl();
//        mBean.setScheduler(scheduler);
//        registerMBean(mBean, "type=scheduler");        
    }

    @Override
    public void close() {
        super.close();
        System.out.println("CERRO");
        if (whiteboardHandler != null) {
            whiteboardHandler.deactivate();
            whiteboardHandler = null;
        }
        if (scheduler != null) {
            scheduler.deactivate();
            scheduler = null;
        }
    }
    
}
