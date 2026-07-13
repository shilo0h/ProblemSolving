import java.util.HashMap;


public class Main {

    public static void main(String[] args) {
        System.out.println("Hello World!");


    }


    private void testingStuff(){
         /*
       7. Frequency Counter
        Input

        {2,3,4,2,4,2,1}

        Output

        2 -> 3
        3 -> 1
        4 -> 2
        1 -> 1

        Use a HashMap<Integer, Integer>.
      */

        int []nums = {2,3,4,2,4,2,1};

        HashMap<Integer, Integer> map = new HashMap<>();
        for(int i = 0; i < nums.length; i++){
            if (!map.containsKey(nums[i])){
                map.put(nums[i], 1);
            }else{
                map.put(nums[i], map.get(nums[i]) + 1);
            }
        }
        System.out.println(map);
    }
}

