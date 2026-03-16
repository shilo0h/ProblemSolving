package threads;

import java.util.concurrent.Executors;

public class VolatileAtomic {
    static void main() {
        final UnsafeClass myClass=new UnsafeClass();
        final int numThreads=2;
        try(final var executorService= Executors.newFixedThreadPool(numThreads)){
            executorService.submit(()->{
                try {
                    myClass.loop();
                }catch (InterruptedException e){
                    throw new RuntimeException(e);
                }
            });
            executorService.submit(myClass::waitToFinish);
            }
        }
    }
