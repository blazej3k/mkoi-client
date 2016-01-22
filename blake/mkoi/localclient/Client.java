package blake.mkoi.localclient;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

import javax.xml.bind.DatatypeConverter;

import blake.mkoi.localclient.crypto.Cipher;
import blake.mkoi.localclient.crypto.Protokol;

public class Client {
	public static Charset charset = StandardCharsets.UTF_8;

	private String serverHost;
	private int serverPort;
	private String klucz="";
	private Cipher cipher;
	private Socket socket;
	private PrintWriter textOut;
	private BufferedReader textIn;
	DataInputStream dis;
	DataOutputStream dos;
	InputStream sockIn;
	OutputStream sockOut;
	
	public Client(String serverHost, int serverPort, String klucz) {
		super();
		this.serverHost = serverHost;
		this.serverPort = serverPort;
		this.klucz = klucz;
		Boolean connected = false;
		
		try {
			connect();
			connected=true;
		} catch (UnknownHostException exc) {
			System.out.println("Host nieznany: "+serverHost);
			exc.printStackTrace();
		} catch (SocketException exc) {
			System.out.println("Błąd połączenia: "+serverHost);
			exc.printStackTrace();
		} catch (IOException exc) {
			System.out.println("Błąd I/O: "+serverHost);
			exc.printStackTrace();
		}

		if (connected) {
			Scanner odczyt = new Scanner(System.in);
			Boolean piszemy = true;
			String message = "";

			while(piszemy) {
				message = odczyt.nextLine(); //wczytanie linii z klawiatury
				send(message);
				
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

    private String receive() {
    	byte[] encrypted = null;
    	String plain="";

        try {
        	dis.read(encrypted);
        	plain = new String(cipher.Decrypt(encrypted), charset);
            return plain;
        }
        catch(IOException e) {
            System.err.println("Błąd odczytu poleceń serwera");
        }
        return Protokol.ERROR;
    }
   
    void send(String command) {
    	byte[] bajtCommand = command.getBytes(charset);
    	
    	byte[] blok = new byte[16];
    	byte[] encrypted=null;
    	
    	for (int i=0; i<=bajtCommand.length/16; i++)
    	{
    		blok = Arrays.copyOfRange(bajtCommand, i*16, (i+1)*16);
    		encrypted = cipher.Encrypt(blok);
    		
        	try {
    			dos.write(encrypted);
    			dos.flush();
    		} catch (IOException e) {
    			System.out.println("Błąd wysyłania tablicy bajtów.");
    			e.printStackTrace();
    		}
    	}
    	

    }
	
	private void sendMessage(String message) {
		try {
			textOut.println(message);
			String lineResp = "";
			boolean jestOdp=false;
			while (((jestOdp==false) & (lineResp = textIn.readLine()) != null))
				System.out.println(lineResp);
				jestOdp=true;
		} catch (IOException exc) {
			System.out.println("Błąd przy wysyłaniu.");
			exc.printStackTrace();
		}
		
		
		
		
//		Cipher szyfrowanie = new Cipher(klucz.getBytes(Cipher.charset));
//		byte[] zaszyfrowane = szyfrowanie.Encrypt("Ala ma kotaAla ma kotaAla ma kotaAla ma kota".getBytes());
//		byte[] odszyfrowane = szyfrowanie.Decrypt(zaszyfrowane);
//		
//		System.out.println(new String(odszyfrowane, Cipher.charset));
	}
	
	private void connect() throws UnknownHostException, IOException {
		socket = new Socket(serverHost, serverPort);

		InputStream sockIn = socket.getInputStream();
		OutputStream sockOut = socket.getOutputStream();

		textIn = new BufferedReader(new InputStreamReader(sockIn));
		textOut	= new PrintWriter(new OutputStreamWriter(sockOut), true);

        dis = new DataInputStream(sockIn);
        dos = new DataOutputStream(sockOut);
		
        cipher = new Cipher(klucz.getBytes(charset));
        
		System.out.println("Połączony  hostem: "+socket.getInetAddress());
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

