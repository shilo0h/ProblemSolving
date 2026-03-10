package designPatterns.factoryPattern;

public class NotificationFactory {

    public static Notification createNotification(String type) {

        if (type == null) {
            return null;
        }

        switch (type.toLowerCase()) {

            case "email":
                return new EmailNotification();

            case "sms":
                return new SmsNotification();

            case "push":
                return new PushNotification();

            default:
                throw new IllegalArgumentException("Unknown notification type");
        }
    }
}
