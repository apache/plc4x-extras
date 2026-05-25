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
package org.apache.plc4x.malbec.s88.plant.nodes;

import java.awt.Image;
import java.io.File;
import javax.imageio.ImageIO;
import org.openide.util.ImageUtilities;

/**
 * Utility to resolve icons for S88 nodes.
 */
public class S88NodeIconUtil {

    public static Image resolveIcon(String iconPath, String defaultResource) {
        if (iconPath != null && !iconPath.isEmpty()) {
            Image img = null;
            try {
                File f = new File(iconPath);
                if (f.isAbsolute() && f.exists()) {
                    img = ImageIO.read(f);
                }
            } catch (Exception e) {
                // Ignore
            }

            if (img == null) {
                String resourcePath = iconPath;
                if (resourcePath.startsWith("/")) {
                    resourcePath = resourcePath.substring(1);
                }
                img = ImageUtilities.loadImage(resourcePath, true);
            }

            if (img != null) {
                return img;
            }
        }
        return ImageUtilities.loadImage(defaultResource);
    }
}
