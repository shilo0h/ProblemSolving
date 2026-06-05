import java.io.*;
import java.time.Duration;
import java.util.*;


public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
            int []nums={1,2,3,4,5,6,7,8,9};
             int window=3;

             int sum=0;

             for (int i=0;i<window;i++){
                 sum+=nums[i];
             }

             int maxSum=sum;
             for(int i=window;i< nums.length;i++){
                 maxSum=maxSum-nums[i-window]+nums[i];

                 if (maxSum>sum){
                     sum=maxSum;
                 }
             }
             System.out.println(sum);
    }
}
