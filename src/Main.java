import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
//        Easy
//        Reverse a singly linked list
//        Detect a cycle in a linked list
//        Find the middle element of a linked list
        MyMultiThread multiThread=new MyMultiThread();
        Thread thread=new Thread(multiThread);
        thread.start();
        MyMultiThread multiThread1=new MyMultiThread();
        Thread thread1=new Thread(multiThread1);
        thread1.start();
    }
}