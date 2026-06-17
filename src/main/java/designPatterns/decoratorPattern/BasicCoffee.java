package designPatterns.decoratorPattern;

public class BasicCoffee implements Coffee{
    @Override
    public double cost() {
        return 5;
    }
}
