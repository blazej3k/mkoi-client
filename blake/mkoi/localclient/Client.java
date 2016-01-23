package blake.mkoi.localclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;
import java.util.StringTokenizer;

import blake.mkoi.localclient.crypto.Cipher;
import blake.mkoi.localclient.crypto.Protokol;

public class Client implements Runnable {
	public static Charset charset = StandardCharsets.UTF_8;

	private String serverHost;
	private int serverPort;
	private String klucz="";
	private Cipher cipher;
	private Socket socket;
	DataInputStream dis;
	DataOutputStream dos;
	InputStream sockIn;
	OutputStream sockOut;
	Boolean connected = false;

	boolean ifWaiting4File = false; // znacznik czy oczekuje na plik (czy wyslalem wczesniej get_pre)
	boolean ifWaiting2Send = false;	// znacznik czy oczekuje na zezwolenie wysyłania pliku (czy wczesniej wyslalem send_pre)
	private File wysylanyPlik;

	public Client(String serverHost, int serverPort, String klucz) {
		super();
		this.serverHost = serverHost;
		this.serverPort = serverPort;
		this.klucz = klucz;

		try {
			connect();
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

		// obsługuje linię poleceń
		new Thread(new Runnable() {
			public void run() {
				obslugaLinii();
			}
		}).start();
	}

	// obsługuje odpowiedzi serwera
	@Override
	public void run() {
		System.out.println("Runnable obsługi odpowiedzi serwera uruchomione.");
		
		String response = "";
		while (connected) {
			response = receiveText();

			System.out.println("Serwer: "+response);
			StringTokenizer st = new StringTokenizer(response);
			String message = st.nextToken();

			System.out.println("Message: "+message+" waiting4file="+ifWaiting4File);

			switch (message) {
			case Protokol.SEND_PRE: {
				if (ifWaiting4File) {
					int rozmiar = Integer.parseInt(st.nextToken());
					String nazwa = st.nextToken();
					sendMessage(Protokol.SEND_ACC);
					receiveFile(rozmiar, nazwa);
				}
				ifWaiting4File = false;
				break;
			}
			case Protokol.SEND_ACC: {
				if (ifWaiting2Send) {
					sendFile(wysylanyPlik);
				}
				
				ifWaiting2Send=false;
				wysylanyPlik = null;
				break;
			}
			case Protokol.ERROR:
				System.out.println("Serwer zgłosił error.");
				break;
			}
		}
	}

	private void obslugaLinii() {
		System.out.println("Wątek obsługi linii poleceń uruchomiony.");
		if (connected) {
			Scanner odczyt = new Scanner(System.in);
			Boolean piszemy = true;
			String message = "";

			while(piszemy) {
				message = odczyt.nextLine(); //wczytanie linii z klawiatury

				switch (message) {
				case Protokol.BYE: {
					piszemy=false;
					odczyt.close();
					System.out.println("Oczekiwanie na zamknięcie połączenia przez serwer.");
					break;
				} 
				case "send": {
					String path = "IMG_1898.jpg";
					wysylanyPlik = new File(path);

					if (wysylanyPlik.exists()) {
						System.out.println("Rozmiar pliku: "+wysylanyPlik.length()+" bajtów");
						sendMessage(Protokol.SEND_PRE+" "+wysylanyPlik.length()+" "+wysylanyPlik.getName());
						ifWaiting2Send = true;
					}		
					break;
				}
				case Protokol.GET_PRE: {
					System.out.println("Podaj nazwę pliku: ");
					String nazwa = odczyt.nextLine();
					ifWaiting4File = true;
					sendMessage(message+" "+nazwa); 
					break;
				}
				default: {
					sendMessage(message);
				}
				}
			}
			dawajInfo();
			disconnect();
		}
	}

	private String receiveText() {
		byte[] bufor		= new byte[64];
		byte[] received;
		int ile = 0;
		byte[] decrypted 	= new byte[64];
		byte[] decryptedBlok = new byte[16];
		byte[] blok = new byte[16];

		Arrays.fill(decrypted, (byte) 0);

		try {
			ile = dis.read(bufor);
			System.out.println("Liczba odebranych: "+ile);
			received = Arrays.copyOfRange(bufor, 0, ile);
			System.out.println("Odebrano ciąg: "+Integer.toBinaryString(received[0]));
		}
		catch(IOException e) {
			System.err.println("Błąd odczytu poleceń serwera.");
			return Protokol.ERROR;
		}

		for (int i=0; i<received.length; i+=16) {
			System.arraycopy(received, i, blok, 0, blok.length);
			decryptedBlok = cipher.Decrypt(blok);
			System.arraycopy(decryptedBlok, 0, decrypted, i, decryptedBlok.length);
		}

		return new String(decrypted, charset).trim();   
	}

	//receiveFile klienta ODSZYFROWYWUJE odebrane dane, nie wykonuje trymowania bo bym musiał plik od nowa wczytać, albo całość najpierw do zmiennej
	// bez sensu by to było. po prostu plik będzie dłuższy o ostatnie dopełnienie do długości bufora (aktualnie do 16KB)
	private String receiveFile(int rozmiar, String nazwa) {
		File folder = new File(".");
		File plik = new File(folder.getParentFile(), nazwa);
		DataOutputStream plikStrumienWy = null;

		System.out.println("Będę odbierać: "+nazwa+" "+rozmiar);

		try {
			plik.createNewFile();
			plikStrumienWy = new DataOutputStream(new FileOutputStream(plik));

		} catch (Exception e) {
			System.out.println("Błąd IO otwarcia pliku do zapisu lub otwarcia strumienia. "+nazwa);
			e.printStackTrace();
		}

		try {
			int c;
			int odebrano=0;
			byte[] bufor = new byte[16];
			while ((c = dis.read(bufor)) != -1) {
				plikStrumienWy.write(cipher.Decrypt(bufor));
				odebrano+=c;
			}
			
			plikStrumienWy.close();
			System.out.println("Deklarowany rozmiar: "+rozmiar+", odebrano: "+plik.length());
		}
		catch(IOException e) {
			System.err.println("Błąd IO odczytu/zapisu strumienia danych: ");
			return Protokol.ERROR;
		}
		catch(IllegalArgumentException e) {
			System.err.println("Błąd IllegalArgumentException odczytu/zapisu strumienia danych: ");
			return Protokol.ERROR;
		}

		System.out.println("Utworzono plik: "+plik.getName()+" o rozmiarze: "+plik.length());

		return new String("x");   
	}
	
	void send(byte[] wejscie, boolean ifText) {
		byte[] blok = new byte[16];
		byte[] encryptedBlok = new byte[16];
		byte[] encryptedTotal=null;
		if (wejscie.length%16!=0) {
			encryptedTotal = new byte[wejscie.length + (16 - wejscie.length%16)];
		}
		else {
			encryptedTotal = new byte[wejscie.length];
		}
		Arrays.fill(encryptedTotal, (byte) 0);
		byte[] wejsciePad = new byte[encryptedTotal.length];
		wejsciePad = Arrays.copyOf(wejscie, wejsciePad.length);

		for (int i=0; i<wejsciePad.length; i+=16) {
			System.arraycopy(wejsciePad, i, blok, 0, blok.length);    		
			encryptedBlok = cipher.Encrypt(blok);
			System.arraycopy(encryptedBlok, 0, encryptedTotal, i, encryptedBlok.length);
		}

		try {
			System.out.println("Wys. do serwera: "+encryptedTotal.length);
//			System.out.println(new String(wejscie, charset));
			dos.write(encryptedTotal);
			dos.flush();
		} catch (IOException e) {
			System.out.println("Błąd wysyłania tablicy bajtów.");
			e.printStackTrace();
		}
	}

	private void sendMessage(String message) {
		send(message.getBytes(charset), true);
	}

	// sendFile klienta SZYFRUJE wysyłane dane
	private void sendFile(File plik) {
		try {
			if (plik.exists()) {
				DataInputStream plikStrumien = new DataInputStream(new FileInputStream(plik));
				System.out.println("Rozmiar pliku: "+plikStrumien.available()+" bajtów.");		

				int ileB=0;
				int razem=0;
				byte[] bufor = new byte[16384];
				while((ileB = plikStrumien.read(bufor))!=-1) {
					send(bufor, false);
					razem+=ileB;
					System.out.println("Wysłałem: "+razem+" bajtów");
				}
				System.out.println("Wyszłem.");
				plikStrumien.close();			
			}
			else {
				System.out.println("Plik nie istnieje.");
			}

		} catch(FileNotFoundException e){
			System.out.println("Nie znaleziono pliku");
		} catch(IOException e){
			System.out.println("Błąd wejścia-wyjścia");
		}
	}

	private void connect() throws UnknownHostException, IOException {
		socket = new Socket(serverHost, serverPort);

		InputStream sockIn = socket.getInputStream();
		OutputStream sockOut = socket.getOutputStream();

		dis = new DataInputStream(sockIn);
		dos = new DataOutputStream(sockOut);

		cipher = new Cipher(klucz.getBytes(charset));

		connected=true;

		System.out.println("Połączony z hostem: "+socket.getInetAddress());
	}

	private void disconnect() {
		try {
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

