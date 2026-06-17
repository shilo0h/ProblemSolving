package designPatterns.strategyPattern;

public class BubbleSort implements SortStrategy{
    @Override
    public void sort(int[] array) {
        System.out.println("This is Bubble sort");
    }
}
