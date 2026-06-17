//import java.sql.Time;
//import java.sql.Timestamp;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.logging.Logger;
//
//public class LoggerSingleton {
//
//    private static LoggerSingleton instance;
//
//    private LoggerSingleton (){
//    }
//
//    public static LoggerSingleton getInstance(){
//        if (instance==null){
//            instance=new LoggerSingleton();
//        }
//        return instance;
//    }
//    public void log(String message){
//        DateTimeFormatter formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
//
//        String timestamp= LocalDateTime.now().format(formatter);
//
//        System.out.println("[" + timestamp + "] " + message);
//    }
//}
//
//LoggerSingleton logger=LoggerSingleton.getInstance();
//
//        logger.log("No one arrived");



//=======================================This is another Singleton Pattern example==============================================