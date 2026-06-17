package threads;

public class VirtualThreads {
    static void main() throws InterruptedException {
        System.out.println(Thread.currentThread().isVirtual());
        Thread.Builder threadBuilder=Thread.ofVirtual().name("myThread");

        Runnable r=()->{
            System.out.println("Hello");
        };
        Thread thread=threadBuilder.start(r);
        thread.join();
    }
}
