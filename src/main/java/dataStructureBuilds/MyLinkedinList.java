package dataStructureBuilds;

public class MyLinkedinList {

    private Node head;

    public MyLinkedinList(){
        head=null;
    }

    public Node addNode(int data){
        Node newNode=new Node(data);
        if(head==null){
            head=newNode;
        }
        else{
            Node temp=head;
            while(temp.next!=null){
                temp=temp.next;
            }
            temp.next=newNode;
        }
        return head;
    }

    public void print(){
        Node temp=head;
        if(head==null){
            return;
        }
        while(temp!=null){
            System.out.println(temp.value);
            temp=temp.next;
        }
    }

    public Node removeNode(int data){

        if(head==null){
            return null;
        }
        if(head.value==data){
            Node temp1=head;
            head=head.next;
            return temp1;
        }
        Node temp = head;

        while (temp.next != null) {
            if (temp.next.value == data) {
                Node removed = temp.next;
                temp.next = temp.next.next;
                return removed;
            }
            temp = temp.next;
        }

        return null;
    }
}
