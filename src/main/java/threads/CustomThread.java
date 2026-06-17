package threads;

public class CustomThread extends Thread{
    private final String tag;

    public CustomThread(final String tag){
        this.tag=tag;
    }

    @Override
    public void run(){

        try{
        for (int i=1;i<=3;i++){
            System.out.println(this.tag+": loop iteration: "+i);
            Thread.sleep(1000);
        }
    } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
