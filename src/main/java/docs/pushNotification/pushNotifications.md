How to send pushNotifications to user

We can use FireBase of googld sdk
we need to generate the accaunt details which are jsn file and put them in our application

The configure our app with those details to connect to our firebase account

Then we need to create a table to save a device token which will be sent via the client when the user
accepts to receive push notifications

Then we create an endpoint to accept the token and save it in db

We can only have token per user cause then if we had more it would be a problem since many user with the same token would receive the notifications
in the device they are registered 

Also need to create an endPoint to delete the token from the db since maybe one user logs out of the account but he still receives the notifications
With this all notification is finished and ready

then we create the function to send notification to the token we want it to send it to 
and put wherever we want