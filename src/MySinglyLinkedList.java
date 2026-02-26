public class MySinglyLinkedList {
    Node head;

    MySinglyLinkedList(){
        head=null;
    }

    public void add(int value){
        Node newNode=new Node(value);
        if (head==null){
            head=newNode;
        }else{
            Node temp = head;
            while (temp.next!=null){
                temp=temp.next;
            }
            temp.next=newNode;
        }
    }
    public void printList() {
        Node temp = head;
        while (temp != null) {
            System.out.print(temp.value + " -> ");
            temp = temp.next;
        }
        System.out.println("null");
    }

    void deleteNode(int key)
    {
        // Store head node
        Node temp = head, prev = null;

        // If head node itself holds the key to be deleted
        if (temp != null && temp.value == key) {
            head = temp.next; // Changed head
            return;
        }

        // Search for the key to be deleted, keep track of
        // the previous node as we need to change temp.next
        while (temp != null && temp.value != key) {
            prev = temp;
            temp = temp.next;
        }

        // If key was not present in linked list
        if (temp == null)
            return;

        // Unlink the node from linked list
        prev.next = temp.next;
    }

    public Node reverse(Node head){
        Node current=head;
        Node previous=null;

        while (current!=null){
            Node newNode=current.next;
            current.next=previous;
            previous=current;
            current=newNode;
        }
        return previous;
    }
}
