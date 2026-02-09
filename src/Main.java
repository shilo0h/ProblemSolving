public class Main {
    public static void main(String[] args) {
        int[] nums = {7, 2, 3, 8, 5, 6, 9, 10, 1, 15};

        quickSort(nums,0, nums.length-1);

        for (int k = 0; k < nums.length; k++) {
            System.out.print(nums[k] + " ");
        }
    }


//=============================================LinearSearch======================================================================================//

    private static void linearSearch(int[] nums, int toFind) {
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] == toFind) {
                System.out.println("Found at position: " + i);
                return;
            }
        }
        System.out.println("Didn't find the number " + toFind);
    }

    //=============================================BinarySearch======================================================================================//
    private static void binarySearch(int[] nums, int toFind) {
        int start = 0;
        int end = nums.length - 1;

        while (start <= end) {
            int middle = start + (end - start) / 2;
            if (nums[middle] == toFind) {
                System.out.println("Found at position: " + middle);
                return;
            }
            if (toFind > nums[middle]) {
                start = middle + 1;
            } else {
                end = middle - 1;
            }
        }
        System.out.println("Not found");
    }

    //=============================================BubbleSort======================================================================================//
    private static void bubbleSort(int[] nums) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = 0; j < nums.length - 1; j++) {
                if (nums[j] > nums[j + 1]) {
                    int temp = nums[j];
                    nums[j] = nums[j + 1];
                    nums[j + 1] = temp;
                }
            }
        }
    }

    //=============================================SelectionSort======================================================================================//
    private static void selectionSort(int[] nums) {
        for (int i = 0; i < nums.length - 1; i++) {
            int min = i;
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[j] < nums[min]) {
                    min = j;
                }
            }
            int temp = nums[i];
            nums[i] = nums[min];
            nums[min] = temp;
        }
    }

    //=============================================InsertionSort======================================================================================//
    private static void insertionSort(int[] nums) {
        for (int i = 1; i < nums.length; i++) {
            int value = nums[i];

            int j = i - 1;
            while (j >= 0 && nums[j] > value) {
                nums[j + 1] = nums[j];
                j--;
            }
            nums[j + 1] = value;
        }
    }


    //=============================================MergeSort======================================================================================//
    private static void mergeSort(int[] nums) {
        int length = nums.length;

        if (length < 2) {
            return;
        }
        int middle = length / 2;
        int[] left = new int[middle];
        int[] right = new int[length - middle];

        for (int i = 0; i < middle; i++) {
            left[i] = nums[i];
        }
        for (int i = middle; i < length; i++) {
            right[i - middle] = nums[i];
        }
        mergeSort(left);
        mergeSort(right);
        merge(nums, left, right);
    }

    private static void merge(int[] nums, int[] left, int[] right) {
        int i = 0;
        int j = 0;
        int k = 0;

        while (i < left.length && j < right.length) {
            if (left[i] < right[j]) {
                nums[k] = left[i];
                i++;
                k++;
            } else {
                nums[k] = right[j];
                j++;
                k++;
            }
        }
        while (i < left.length) {
            nums[k] = left[i];
            i++;
            k++;
        }
        while (j < left.length) {
            nums[k] = right[j];
            j++;
            k++;
        }
    }


//=============================================QuickSort======================================================================================//
private static void quickSort(int[] nums, int lowIndex, int hightIndex) {
    if (lowIndex >= hightIndex) {
        return;
    }

    int pivot = nums[hightIndex];
    int leftPointer = lowIndex;
    int rightPointer = hightIndex -1;

    while (leftPointer <= rightPointer) {

        while (leftPointer <= rightPointer && nums[leftPointer] <= pivot) {
            leftPointer++;
        }

        while (leftPointer <= rightPointer && nums[rightPointer] >= pivot) {
            rightPointer--;
        }

        if (leftPointer < rightPointer) {
            int temp = nums[leftPointer];
            nums[leftPointer] = nums[rightPointer];
            nums[rightPointer] = temp;
        }
    }

    int temp = nums[leftPointer];
    nums[leftPointer] = nums[hightIndex];
    nums[hightIndex] = temp;

    quickSort(nums, lowIndex, leftPointer - 1);
    quickSort(nums, leftPointer + 1, hightIndex);
}


}