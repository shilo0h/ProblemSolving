package designPatterns.strategyPattern;

public class QuickSort implements SortStrategy{
    @Override
    public void sort(int[] array) {
        System.out.println("This is quick sort");
    }
}
