package threads;

import java.util.concurrent.Executors;

public class Synchronize {
    private static final int NUM_THREADS=10;
    private static final int NUM_ITERS=100_000;

    static void main() {
    final var data=new DataHolder();
        try(final var executor= Executors.newFixedThreadPool(NUM_THREADS)){
            for (int i=0;i<NUM_THREADS;i++){
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        for (int j=0;j<NUM_ITERS;j++){
                            data.increment();
                        }
                    }
                });
            }
        }
        System.out.println("Expected: "+(NUM_THREADS*NUM_ITERS)
        +",actual: "+data.getData());
    }
}
