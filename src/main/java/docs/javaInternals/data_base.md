JPQL vs native query   

JPQL is where you talk with the java classes
jpql ->hibernate translates to sql -> executed as sql
native query -> sql ->executed

casscade Cascade ALl -> means changes that happen to parent applie to childs as well so if parent deleted child is deleted also
fetch type-> means how the entity is loaded when is called for example eager loads other object that entity might have while 
lazy only loads the object if want to load other objects inside need to call them

@Lock  this makes the query to lock so other cant execute that method or transaction while one being executed

@EntityGraph(attributePaths = "payment")  this is used when payment object is lazy somewhere but in this query where EntityGraph is used can be loaded not lazy