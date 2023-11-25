import java.net.Socket;

public class client {
  public static void main(String[] argv){
    
    if(argv.length!= 2){
      System.err.println("usage: client <hostname> <hostport>");
      System.exit(1);
    }

    try{
      TCPStart.start();
      //TCPWrapper.dropSelectedPacket(1); //SYN is lost
      //TCPWrapper.dropSelectedPacket(2); //ACK is lost
      
      Socket sock = new Socket(argv[0], Integer.parseInt(argv[1]));

      System.out.println("got socket "+sock);
      
      Thread.sleep(10*1000);
    }
    catch(Exception e){
      System.err.println("Caught exception:");
      e.printStackTrace();
    }
  }
}
