package threads;

import java.util.concurrent.Semaphore;

public class MySemaphore {
    static void main() throws InterruptedException {
        Semaphore semaphore=new Semaphore(3);
        semaphore.acquire(3);
        semaphore.acquire();
        semaphore.release();
        System.out.println(semaphore.availablePermits());
        semaphore.getQueueLength();
    }
}
