package threads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Threading {

    static void main() throws InterruptedException {

        //This is the way to declare and create a Thread

        CustomThread thread1=new CustomThread("Cat");
        CustomThread thread2=new CustomThread("Dog");
        CustomThread thread3=new CustomThread("Bird");

        //This start the thread executing the run method

        thread1.start();
        thread2.start();
        thread3.start();

        //This makes it so these threads finish first then we continue with other functions

        thread1.join();
        thread3.join();
        thread2.join();

        System.out.println("Done");

        //This is another way of creating threads implementing Runnable

        CustomRunnable runnable1=new CustomRunnable("Cat");
        CustomRunnable runnable2=new CustomRunnable("Dog");
        CustomRunnable runnable3=new CustomRunnable("Bird");
        new Thread(runnable1).start();
        new Thread(runnable2).start();
        new Thread(runnable3).start();

        //This is another way of creating threads

        final int numThreads=3;
        ExecutorService executorService= Executors.newFixedThreadPool(numThreads);
        for (int i=0;i<numThreads;i++){
            executorService.submit(new CustomRunnable("test" + i));
        }
        executorService.shutdown();
    }
}
