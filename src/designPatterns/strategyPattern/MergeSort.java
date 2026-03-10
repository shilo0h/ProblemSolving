package designPatterns.strategyPattern;

public class MergeSort implements SortStrategy{
    @Override
    public void sort(int[] array) {
        System.out.println("This is merge sort");
    }
}
