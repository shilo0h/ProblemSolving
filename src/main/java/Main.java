import jdk.dynalink.linker.LinkerServices;

import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;


public class Main {

    public static void main(String[] args) throws InterruptedException{

        int[]nums = {1,7,3,2};
        int sum=9;
        HashMap<Integer,Integer> map = new HashMap<>();
        for(int i=0;i<nums.length;i++){
            map.put(nums[i],i);
        }
        for (int i=0;i<nums.length;i++){
            if(map.containsKey(sum-nums[i])){
                System.out.println("Found element at position with sum "+sum+"at positon "+map.get(sum-nums[i]) +" and "+map.get(nums[i]));
                break;
            }
        }
        System.out.println("Not found");
    }
}