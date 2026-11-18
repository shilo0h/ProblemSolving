cpu hav multiple threads 

the more cpu the more executions can cpu make parallel and higher the speed the better the performance

process is an execution of something for example in my case sending message

thread is execution of a task in a process for example one calls api controller another thread send to service another to kafka and so on

Runnable = defines the task you want to execute.

Thread = provides the actual thread that executes the task.

thread.start executes the runnable inside the current thread
thread.run executes the current thread 

Runnable describes the work. Thread executes the work.


A ThreadPool is a collection of reusable threads that execute tasks. and you can modify it for the task you want to assign it to

Thread vs Virtual threads:Threads are physical that communicate with cpu and they are numbers how much the cpu has while virtual threads 
you can create as many as you want) the difference is that threads when start a process if it takes time they wait till the proces is finished then continue with other task
while virtual threads they dont wait they continue with other task then continue with the task they were doing when is finished

Executor is an interace that manages a thread pool


An interface defines what something can do. An implementation defines how it actually does it.
So if a class implement an interce you can make a function where return is interface and you inside you can build new object that implements it and
return that and it works what i mean is 
public Interface(){
    Object that implement it =new class();

    return object
}

and it will work or you can do also Interface interface =new Class that implement it();