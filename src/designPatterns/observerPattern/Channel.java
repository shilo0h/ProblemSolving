package designPatterns.observerPattern;

import java.util.ArrayList;
import java.util.List;

public class Channel {

    private List<Observer> subscribers = new ArrayList<>();

    public void subscribe(Observer o) {
        subscribers.add(o);
    }

    public void notifySubscribers(String video) {
        for(Observer s : subscribers) {
            s.update(video);
        }
    }
}