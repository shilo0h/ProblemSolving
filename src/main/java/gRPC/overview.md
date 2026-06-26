First we add the necessary dependencies and the plugins 

Then we create a proto files were we define the functions and the object we want 


Then we compile the project the files are generated

In another class then we extend the generated class

We overide the methods we want and put our own implementation

We also need to define where our gRPC server is going to run

And this is how to test and send the request to gRPC server

to return response gRPC uses StreamObserver<T> is the object gRPC gives you to send data back to whoever called your service.
Think:return response;

So instead of:BillingResponse create(...)
you do:  responseObserver.onNext(response);  sending data
responseObserver.onCompleted();   finished sending data

GRPC localhost:9001/BillingService/CreateBillingAccount

{
"patientId": "12333",
"name" : "John Doe",
"email" : "john.doe@example.com"
}


To communicate with different microservices via gRPC we need to create a gRPC client on the service we want to call it from 
and then we call it in the function
