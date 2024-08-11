package org.apache.plc4x.merlot.grpc.impl;

import org.apache.plc4x.merlot.grpc.api.GrpcServer;
import io.grpc.BindableService;
import io.grpc.LoadBalancerRegistry;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorldServer implements GrpcServer {

    private static final Logger LOG = LoggerFactory.getLogger(HelloWorldServer.class);
    private final int port = 5000;
    private Server server;
    
    private MutableHandlerRegistry servicesHandler = new MutableHandlerRegistry(); 
    private BindableService greeterService;

    public void init() {
        try {
            //DnsNameResolverProvider
            NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());
            LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
            
            LOG.info("NRR: "  +NameResolverRegistry.getDefaultRegistry().getDefaultScheme());
            
            server = NettyServerBuilder
                    .forPort(port)
                    .fallbackHandlerRegistry(servicesHandler)
                    //.addService(greeterService)
                    .build()
                    .start();
            LOG.info("Server started, listening on {}", port);
            CompletableFuture.runAsync(() -> {
                try {
                    server.awaitTermination();
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            });
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public void setGreeterService(BindableService greeterService) {
        this.greeterService = greeterService;
    }

    public void destroy() {   
        if (server != null) {
            server.shutdown();
        }
    }
    
    public void bind(BindableService refService) {
        LOG.info("BIND");
        if (refService == null) return;        
        servicesHandler.removeService(refService.bindService());   
        servicesHandler.addService(refService);
    }    
    
    public void unbind(BindableService refService) {        
        LOG.info("UNBING");  
        if (refService == null) return;          
        servicesHandler.removeService(refService.bindService());
    }    


}
