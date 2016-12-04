### MODEL ###
User (name, email)

### FUNCTIONALITIES ###
- Create a user from a FORM 
- List all users 
- Search an user by name
- Webservice to return a user's json 
- Webservice to create a user from a json

### HOW TO RUN ###
sbt clean 
sbt compile
sbt run 

### DATABASE ###
mongodb 

### WEBAPP ###
http://localhost:9000/

### WEBSERVICES ###

-Get All Users
curl http://localhost:9000/websUsers

-Get All Users By Name 
curl http://localhost:9000/websUsers/name=Peter

-Post Json New User
curl -H "Content-Type: application/json" -X POST -d '{"name":"user","email": "email@user.co.uk"}' http://localhost:9000/websUsers/