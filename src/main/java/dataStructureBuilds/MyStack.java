package dataStructureBuilds;

public class MyStack {
    private int []stack;
    private int top = -1;
    private int size;

    public MyStack(int size){
        this.size = size;
        stack = new int[size];
        top=-1;
    }


    public boolean isEmpty(){
        if(top==-1){
            return true;
        }
        return false;
    }

    public boolean isFull(){
        if(top==size-1){
            return true;
        }
        return false;
    }

    public int peek(){
        if(top==-1){
            return -1;
        }
        return stack[top];
    }

    public void push(int x){
        if (isFull()){
            System.out.println("You stack is Full");
        }else{
            top++;
            stack[top] = x;
        }
    }

    public int pop(){
        if(isEmpty()){
            System.out.println("You stack is Empty");
        }
        int x=stack[top];
        top--;
        return x;
    }
}
