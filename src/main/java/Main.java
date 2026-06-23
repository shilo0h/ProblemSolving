import java.util.Arrays;

public class Main {

    static void main() {
        int nums[]={2,34,434,6546,546,546,45,4532,3,32};

        mergeSort(nums);
        for (int num : nums) {
            System.out.println(num);
        }
    }


    private static void mergeSort(int[]nums){
        int len=nums.length;
        if(len<2) return;
        int mid=len/2;
        int[] left=new int[mid];
        int[] right=new int[len-mid];
        for(int i=0;i<mid;i++){
            left[i]=nums[i];
        }
        for(int i=mid;i<len;i++){
            right[i-mid]=nums[i];
        }
        mergeSort(left);
        mergeSort(right);
        merge(nums,left,right);
    }
    private static void merge(int[] nums,int[] left,int[] right){
        int i=0,j=0,k=0;
        while(i<left.length&&j<right.length){
            if(left[i]<=right[j]){
                nums[k++]=left[i++];
            }
            else{
                nums[k++]=right[j++];
            }
        }
        while(i<left.length){
            nums[k++]=left[i++];
        }
        while(j<right.length){
            nums[k++]=right[j++];
        }
    }
}