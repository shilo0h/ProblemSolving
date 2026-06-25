//package com.klaos.billingservice.grpc;
//
//import billing.BillingRequest;
//import billing.BillingResponse;
//import billing.LastName;
//import billing.Name;
//import io.grpc.stub.StreamObserver;
//import net.devh.boot.grpc.server.service.GrpcService;
//import billing.BillingServiceGrpc.BillingServiceImplBase;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//@GrpcService
//public class OurImplementation extends BillingServiceImplBase{
//    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);
//
//    @Override
//    public void createBillingAccount(BillingRequest billingRequest,
//                                     StreamObserver<BillingResponse> responseObserver) {
//
//        log.info("createBillingAccount request received{}",billingRequest.toString());
//
//        BillingResponse response=BillingResponse.newBuilder()
//                .setAccountId("12345")
//                .setStatus("ACTIVE")
//                .build();
//
//        responseObserver.onNext(response);
//        responseObserver.onCompleted();
//    }
//
//    @Override
//    public void testingSomething(Name name, StreamObserver<LastName> responseObserver) {
//        LastName lastName = null;
//        if (name.getName().equals("klaos")){
//            lastName=LastName.newBuilder()
//                    .setLastName("lleshi")
//                    .build();
//        }else{
//            lastName= LastName.newBuilder().
//                    setLastName("not the one")
//                    .build();
//        }
//        responseObserver.onNext(lastName);
//        responseObserver.onCompleted();
//    }
//
//}
