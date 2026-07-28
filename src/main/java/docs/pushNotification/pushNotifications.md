How to send pushNotifications to user

We can use FireBase of googld sdk
we need to generate the accaunt details which are jsn file and put them in our application

The configure our app with those details to connect to our firebase account

Then we need to create a table to save a device token which will be sent via the client when the user
accepts to receive push notifications

Then we create an endpoint to accept the token and save it in db

then we create the function to send notification to the token we want it to send it to 
and put wherever we want