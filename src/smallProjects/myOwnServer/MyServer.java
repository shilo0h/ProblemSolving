//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.PrintWriter;
//import java.net.ServerSocket;
//import java.util.Scanner;
//import java.util.concurrent.Executors;
//
//public class MyServer {
//    public void start(final int portNumber){
//        try(var serverSocket=new ServerSocket(portNumber)){
//            try(var executor= Executors.newVirtualThreadPerTaskExecutor()){
//                while (true){
//                    var client=serverSocket.accept();
//                    executor.submit(()->{
//                        System.out.println("Client connected");
//                        var clientIp=client.getInetAddress().getHostAddress();
//                        var clientPort=client.getPort();
//                        try(var clientInput=new BufferedReader(new InputStreamReader(client.getInputStream()));
//                            var output=new PrintWriter(client.getOutputStream(),true)){
//                            for (String inputLine;(inputLine=clientInput.readLine())!=null;){
//                                System.out.println(STR."\{clientIp}:\{clientPort}:\{inputLine}");
//                                output.println(new StringBuilder(inputLine).reverse());
//                            }
//                        }
//                        catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
//                    });
//                }
//            }
//        }catch (IOException e){
//            throw new RuntimeException(e);
//        }
//    }
//}
//
//
//final int PORT_NUMBER=12345;
//
//void main(){
//    try(var scanner=new Scanner(System.in)) {
//        System.out.println("Is this a server? (y/n)");
//        if (scanner.nextLine().equalsIgnoreCase("y")){
//            new MyServer().start(PORT_NUMBER);
//        }else{
//            new MyClient().start(PORT_NUMBER,scanner);
//        }
//    }
//}