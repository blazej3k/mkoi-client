package blake.mkoi.localclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

import blake.mkoi.localclient.crypto.Cipher;

public class Client {
	private String serverHost;
	private int serverPort;
	private Socket socket;
	private PrintWriter textOut;
	private BufferedReader textIn;
	InputStream sockIn;
	OutputStream sockOut;
	
	public Client(String serverHost, int serverPort) {
		super();
		this.serverHost = serverHost;
		this.serverPort = serverPort;
		
		Boolean connected = false;
		String klucz = "KutasKutasKutasK";
		
		Cipher szyfrowanie = new Cipher(klucz.getBytes(Cipher.charset));
		byte[] zaszyfrowane = szyfrowanie.Encrypt("Ala ma kotaAla ma kotaAla ma kotaAla ma kota".getBytes());
		byte[] odszyfrowane = szyfrowanie.Decrypt(zaszyfrowane);
		
		System.out.println(new String(odszyfrowane, Cipher.charset));
		
//		try {
//			connect();
//			connected=true;
//		} catch (UnknownHostException exc) {
//			System.out.println("Host nieznany: "+serverHost);
//			exc.printStackTrace();
//		} catch (SocketException exc) {
//			System.out.println("Błąd połączenia: "+serverHost);
//			exc.printStackTrace();
//		} catch (IOException exc) {
//			System.out.println("Błąd I/O: "+serverHost);
//			exc.printStackTrace();
//		}

		if (connected) {
			Scanner odczyt = new Scanner(System.in);
			Boolean piszemy = true;
			
			String message = "";
			//message = "Kutas 123";
			message = odczyt.nextLine();
			
			System.out.println("Będę wysyłać: "+message);

			while(piszemy) {
				sendMessage(message);
				System.out.println("poszło");
				
				if (message == "bye") {
					piszemy=false;
					odczyt.close();
					System.out.println("Oczekiwanie na zamknięcie połączenia przez serwer.");
				}
				
			}
			
			dawajInfo();
			disconnect();
		}
	}

	private void connect() throws UnknownHostException, IOException {
		socket = new Socket(serverHost, serverPort);

		InputStream sockIn = socket.getInputStream();
		OutputStream sockOut = socket.getOutputStream();

		textIn = new BufferedReader(new InputStreamReader(sockIn));
		textOut	= new PrintWriter(new OutputStreamWriter(sockOut), true);

		System.out.println("Połączony  hostem: "+socket.getInetAddress());
	}

	private void sendMessage(String message) {
		try {
			textOut.println(message);
			String lineResp = "";
			while ((lineResp = textIn.readLine()) != null) 
				System.out.println(lineResp);
		} catch (IOException exc) {
			System.out.println("Błąd przy wysyłaniu.");
			exc.printStackTrace();
		}
	}
	
	private void disconnect() {
		try {
			textIn.close();
			textOut.close();
			sockIn.close();
			sockOut.close();
			socket.close();
		} catch (IOException exc) {
			System.out.println("Błąd przy rozłączaniu:");
			exc.printStackTrace();
		}
	}
	
	private void dawajInfo() {
		Method[] methods = (java.net.Socket.class).getMethods();
	    Object[] args = {};
	    for (int i=0; i<methods.length; i++) {
	      String name = methods[i].getName();
	      if ((name.startsWith("get") || name.startsWith("is")) &&
	          !name.equals("getChannel") &&
	          !name.equals("getInputStream") &&
	          !name.equals("getOutputStream")) {

	        try {
				System.out.println(name + "() = " +
				                   methods[i].invoke(socket, args));
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	      }
	    }
	}
}
