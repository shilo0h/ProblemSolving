package threads;

public class DataHolder {
    private int data;

    public int getData(){
        return this.data;
    }

    //synchronized means that only one thread can enter this method at any give time
    public  void increment(){
        synchronized (this) {
            this.data++;
        }
    }
}
