java -Dlogging.file.name=/var/log/myapp/application.log -jar app.jar

What this does is start Spring boot app and in runtime it creates a logging file
where are the console logs will display there in a directory that is already created
This is one way to create in runtime 

The other way is to specify it in application.properties or yml like this
logging.file.name=/var/log/myapp/application.log