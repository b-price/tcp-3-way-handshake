import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  //   protected InetAddress address;
  //   protected int port;
  //   protected int localport;

  // Define variables here, such as States, SEQ/ACK numbers, and addresses.
  private String state;
  private int seqNum;
  private int ackNum;
  private boolean synFlag;
  private boolean ackFlag;
  private Demultiplexer D;
  private Timer tcpTimer;
  private InetAddress sendAddress;
  private int destPort;
  
  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
    // Initialize variables here, including state, Seq/Ack numbers
    state = "CLOSED";
    seqNum = 0;
    ackNum = 0;
    synFlag = false;
    ackFlag = false;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException {

    // Set local variables here
    // i.e., source/destination IP address, source/destination ports, sequence number
    sendAddress = address;
    localport = D.getNextAvailablePort();

    // Register Connection Socket to Demux here
    D.registerConnection(address, localport, port, this);

    // Send a SYN packet and change state here. You may want to create and call a wrapper function.
    synFlag = true;
    TCPPacket sendSYN = new TCPPacket(
            localport, port, seqNum, ackNum, ackFlag, synFlag, false, 0, null);
    TCPWrapper.send(sendSYN, address);
    System.out.print(state + " -> ");
    state = "SYN_SENT";
    System.out.println(state);

    // Wait until the ESTABLISHED state here
    // while ...
    while (!state.equals("ESTAB")) {
      try {
        wait();
        createTimerTask(5*1000, sendSYN);
      } catch (InterruptedException e) {
        System.out.println("check the packet");
      }
    }
    tcpTimer.cancel();
  }

  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p) throws IOException {

    // Print out a received packet here
    System.out.println(p.getDebugOutput());
    sendAddress = p.sourceAddr;
    destPort = p.sourcePort;

    // For other states, process the received packets accordingly, including the packet transmission, timer behavior, state changing.
    switch (state){
      case "LISTEN":
        if (p.synFlag == true && p.ackFlag == false){
          D.unregisterListeningSocket(p.destPort, this);
          D.registerConnection(p.sourceAddr, p.destPort, p.sourcePort, this);
          System.out.print(state + " -> ");
          state = "SYN_RCVD";
          System.out.println(state);
          synFlag = true;
          ackFlag = true;
          seqNum = p.seqNum + 1;
          TCPPacket synAck = new TCPPacket(
                  p.destPort, destPort, seqNum, ackNum, ackFlag, synFlag, false, 0,null);
          TCPWrapper.send(synAck, sendAddress);
          break;
        }
        else System.err.println("Error. Non-syn packet received.");
        break;
      case "SYN_SENT":
        if (p.synFlag == true && p.ackFlag == true){
          System.out.print(state + " -> ");
          state = "ESTAB";
          System.out.println(state);
          synFlag = false;
          ackFlag = true;
          seqNum = p.seqNum +1;
          TCPPacket ack = new TCPPacket(
                  p.destPort, destPort, seqNum, ackNum, ackFlag, synFlag, false, 0,null);
          TCPWrapper.send(ack, sendAddress);
          break;
        }
        else System.err.println("Error. Non-syn+ack packet received.");
        break;
      case "SYN_RCVD":
        if (p.synFlag == false && p.ackFlag == true){
          System.out.print(state + " -> ");
          state = "ESTAB";
          System.out.println(state);
          synFlag = false;
          ackFlag = false;
          seqNum = p.seqNum +1;
          break;
        }
        else System.err.println("Error. Non-ack packet received.");
        break;
      case "ESTAB":
        System.out.println("3-way handshake complete.");
        break;
      default:
        System.err.println("Error. Invalid state.");
    }

    this.notifyAll();
  }

  /**
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling
   * ServerSocket.accept(), but this method belongs to the Socket object
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {

    // Register Listening Socket to demux 
    D.registerListeningSocket(localport, this);

    // Change state to LISTEN and initialize Sequence number here
    System.out.print(state + " -> ");
    state = "LISTEN";
    System.out.println(state);
    seqNum++;
    TCPPacket ack = new TCPPacket(
            localport, destPort, seqNum, ackNum, ackFlag, synFlag, false, 0,null);
    // Wait until the ESTABLISHED state
    // while ...
    while (!state.equals("ESTAB")) {
      try {
        wait();
        createTimerTask(5*1000, ack);
      } catch (InterruptedException e) {
        System.out.println("check the packet");
      }
    }
    tcpTimer.cancel();
  }


  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    return null;

  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    return null;
  }


  /**
   * Closes this socket.
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
  }

  /**
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return
   * information.
   */
  public synchronized void handleTimer(Object ref){

    TCPPacket packet = (TCPPacket)ref;

    // Retransmit packet here
    TCPWrapper.send(packet, sendAddress);
    // Reset Timers here
    tcpTimer.cancel();

    this.notifyAll();
  }

}
