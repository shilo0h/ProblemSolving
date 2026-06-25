First we add the necessary dependencies and the plugins 

Then we create a proto files were we define the functions and the object we want 


Then we compile the project the files are generated

In another class then we extend the generated class

We overide the methods we want and put our own implementation

We also need to define where our gRPC server is going to run

And this is how to test and send the request to gRPC server

GRPC localhost:9001/BillingService/CreateBillingAccount

{
"patientId": "12333",
"name" : "John Doe",
"email" : "john.doe@example.com"
}
