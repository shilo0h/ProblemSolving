package dataStructureBuilds;

public class MyStack {

    private int[] arr;

    // maximum size of stack
    private int capacity;

    // index of top element
    private int top;

    // constructor
    public MyStack(int cap) {
        capacity = cap;
        arr = new int[capacity];
        top = -1;
    }

    void push(int x) {
        if (top == capacity - 1) {
            System.out.println("Stack Overflow");
            return;
        }
        arr[++top] = x;
    }

    void print(){
            for (int i=0;i<arr.length;i++){
                System.out.println(arr[i]);
            }
        }
}
