package designPatterns.factoryPattern;

public class SmsNotification implements Notification{
    @Override
    public void send(String message){
        System.out.println("Sending Sms: "+message);
    }
}
