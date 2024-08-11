package org.apache.plc4x.merlot.grpc.impl;


import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import org.apache.plc4x.merlot.api.GreeterGrpc;
import org.apache.plc4x.merlot.api.HelloReply;
import org.apache.plc4x.merlot.api.HelloRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreeterService extends GreeterGrpc.GreeterImplBase implements BindableService{

    private static final Logger LOG = LoggerFactory.getLogger(GreeterService.class);

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        LOG.info("sayHello endpoint received request from " + request.getName());
        HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
    
    

}
