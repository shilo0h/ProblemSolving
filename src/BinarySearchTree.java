public class BinarySearchTree {
    /* Binary search tree-------------has only 2 childresns dega e
     djatht duhet te jete me e madhe se e majta dmth cdo numer qe hyn kontrollon nga root deri kur te shkoje ne fund
     krijohet me funksione rekursive te cilat thirrin vetveten deget jane te lidhura me njera tjetren
     Krijohet nje klase Node e cila ka int vleren dhe dy Node te majten dhe te djathten te cilat fillimisht
     jane null ndersa Binary search treeja ka nje Node root e cila esht fillimi i cdo funksioni  ne cdo funksion
     fillimisht gjejme nqs Node ku jemi nuk esht null pastaj kontrollojme nqs esht me e madhe apo me e vogel dhe keshtu me rradhe behen dhe funksionet e tjera.
    */

    Node root;

    private Node addNode(Node root,Node node){
        int data= node.value;
        if (root==null){
            this.root=node;
            return root;
        }
        if (data< root.value){
            root.left=addNode(root.left,node);
        } else if (data>root.value) {
            root.right=addNode(root.right,node);
        }
        return node;
    }

    public void add(Node node){
        root=addNode(root,node);
    }

}
