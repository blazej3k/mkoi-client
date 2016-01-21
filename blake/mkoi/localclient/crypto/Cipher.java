package blake.mkoi.localclient.crypto;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Cipher {
	
	public static Charset charset = StandardCharsets.UTF_8;
	
	private String text = "";
	
	private int w 			= 32; // bits - 4 B
	private int wordB		= 4;
	private int rounds 		= 20;
	private int keySize		= 16; // bytes, może być 16,24 lub 32 BAJTY, ale założeniem projektu jest 16
	private int logW		= 5; // lg(wordSize)
	
	private int stalaGalaktyczna1 = 0xB7E15163; // stałe potrzebne przy inicjacji kluczy rundowych
	private int stalaGalaktyczna2 = 0x9E3779B9;
	
	private Integer[] S		= new Integer[2*rounds +4]; // tablica na klucze  
	private int A,B,C,D		= 0;	// rejestry A,B,C,D - każdy po 4 B
	private byte[] klucz;
	
	public Cipher(byte[] klucz) {
		this.klucz = klucz;
		
		InicjujKlucze();
	}
	
	private void InicjujKlucze() {
		// wciaga tablice bajtow z kluczem o dlugosci keySize
		// wypluwa tablice S[44] (indeksy do 43) kluczy rundowych czyli 2 * rounds plus 4
		
		// najpierw trzeba klucz usera wprowadzić do tablicy int L (4 razy mniejsza niż byte[] klucz), w sposób jak dane do buforów ABCD
		// klucz ma 16, 24 lub 32 BAJTY czyli L = klucz.length/4
		
		int[] L 	= new int[keySize/4];
		int c 		= L.length;
		int offset	= 0;
		
		for (int i=0; i<L.length; i++) {
			L[i] = 0;
			L[i] = ((klucz[offset++] & 0xFF)) | ((klucz[offset++] & 0xFF) << 8) | ((klucz[offset++] & 0xFF) << 16) | ((klucz[offset++] & 0xFF) << 24);
		}
		
		for(int i=0; i<S.length; i++) {
			S[i] = 0;
		}
		
		S[0] = stalaGalaktyczna1;
		for(int i=1; i<S.length; i++) {
			S[i] = S[i-1] + stalaGalaktyczna2;
		}
		
		int aT=0, bT=0, i=0, j=0;
		for (int s=1; s<=132; s++) {
			aT = S[i] = rotL(S[i] + aT + bT, 3);
			bT = L[j] = rotL(L[j] + aT + bT, aT+bT);
			
			i = (i+1) % 44;
			j = (j+1) % c;
		}
		
	}
	
	public byte[] Encrypt(byte[] plain) {
		int t=0, u=0, x=0;
		
		String test = new String(plain, charset);
		
		System.out.println("Szyfruje se teksta: "+test);
		byte[] bajtBufor = test.getBytes(charset);
		System.out.println("Jego długość bajtowa: "+bajtBufor.length+ ", znakowa: "+test.length());
		System.out.println();
		
		InicjujBufory(plain);
		
		B = B + S[0];
		D = D + S[1];
		
		for(int i=1; i<=rounds; i++) {
			t = B*(2*B + 1);
			t = rotL(t, logW);
			
			u = D*(2*D + 1);
			u = rotL(u, logW);
			
			A = rotL(A^t, u) + S[2*i];
			C = rotL(C^u, t) + S[2*i + 1];
			
			x=A;
			A=B; B=C; C=D; D=x;
			//parallel assignment (A,B,C,D) = (B,C,D,A)
		}
		
		A = A+S[2*rounds + 2];
		C = C+S[2*rounds + 3];
		
		
		//uporządkowanie wyników, zebranie ich z buforów i skonstruowanie tablicy bajtów wyjściowych (zakodowanych)
		int[] intWynik = new int[plain.length/4];
		byte[] byteWynik = new byte[plain.length];
		intWynik[0] = A;intWynik[1] = B;intWynik[2] = C;intWynik[3] = D;

		for(int i = 0;i<byteWynik.length;i++){
			byteWynik[i] = (byte)((intWynik[i/4] >>> (i%4)*8) & 0xff);
		}

	     return byteWynik;
	}
	
	public byte[] Decrypt(byte[] encrypted) {
		int t=0, u=0, x=0;
		InicjujBufory(encrypted);
		
		C = C - S[2*rounds+3];
		A = A - S[2*rounds+2];
		
		for (int i=rounds; i>=1; i--) { 
			x=D; 
			D = C; 
			C = B; 
			B = A; 
			A = x;
			
			u = D*(2*D+1);
			u = rotL(u, logW);
			
			t = B*(2*B+1);
			t = rotL(t, logW);
			
			C = rotR(C-S[2*i+1], t);
			C = C^u;
			A = rotR(A-S[2*i], u);
			A = A^t;
		}
		
		D = D - S[1];
		B = B - S[0];
		
		//uporządkowanie wyników, zebranie ich z buforów i skonstruowanie tablicy bajtów wyjściowych (jawnych)
		int[] intWynik = new int[encrypted.length/4];
		byte[] byteWynik = new byte[encrypted.length];
		intWynik[0] = A;intWynik[1] = B;intWynik[2] = C;intWynik[3] = D;

	     for(int i = 0;i<byteWynik.length;i++){
	    	 byteWynik[i] = (byte)((intWynik[i/4] >>> (i%4)*8) & 0xff);
	     }
	     
	     return byteWynik;
	}
	
	private void InicjujBufory(byte[] wejscie) {
		int offset=0;
		int[] temp = new int[wejscie.length/4];
		
		for (int i=0; i<temp.length; i++) {
			temp[i]=0;
		}
		A=0;B=0;C=0;D=0;
		
		// The first byte of plaintext or ciphertext is placed in the least signicant byte of A  
		// the last byte of plaintext or ciphertext is placed into the most signicant byte of D  
		
		for (int i=0; i<temp.length; i++) {
			temp[i] = (wejscie[offset++] & 0xFF) | ((wejscie[offset++] & 0xFF) << 8) | ((wejscie[offset++] & 0xFF) << 16) | ((wejscie[offset++] & 0xFF) << 24);
			// to mam teraz wypełnioną jedną linijkę intów, czyli styknie na 1 rejestr, 4 bajtowy, np. A
			// w kolejnym obrocie będzie B, w ostatnim 'last byte of plaintext' trafi do pierwszego bajtu D = dobrze
			// przez przesunięcia i użycie inta bufory będą równe, nie trzeba ich specjalnie pilnować i dopełniać
		}
		A = temp[0];
		B = temp[1];
		C = temp[2];
		D = temp[3];
	}
	
	
	private int rotL(int dane, int n) {
		//32 to size inta
		// mozna by w sumie przesuwac w prawo >>, ze znakiem, bez znaczenia bo i tak to co 'wciagam' z powrotem jest robione ta druga operacja
		int wynik = (dane << n) | (dane >>> (32-n));
		return wynik;
	}
	
	private int rotR(int dane, int n) {
		//32 to size inta
		int wynik = (dane >>> n) | (dane << (32-n));
		return wynik;
	}
}
