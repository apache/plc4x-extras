/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.plc4x.malbec.s88.plant.services;

import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88RepositoryProvider;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.openide.util.Lookup;

/**
 * NetBeans integration service for S88 repositories.
 */
public final class S88ProjectServices {

    public static S88Repository createRepository(String format, S88Storage storage) {
        var providers = Lookup.getDefault().lookupAll(S88RepositoryProvider.class);
        System.out.println("[S88ProjectServices] Searching for provider: " + format);
        System.out.println("[S88ProjectServices] Found " + providers.size() + " providers in Lookup.");

        for (S88RepositoryProvider p : providers) {
            System.out.println("[S88ProjectServices] Checking provider: " + p.getClass().getName());
            if (p.accepts(format)) return p.createRepository(storage);
        }
        throw new IllegalArgumentException("Unsupported format: " + format);
    }
}