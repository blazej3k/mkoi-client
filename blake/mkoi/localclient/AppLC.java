package blake.mkoi.localclient;

public class AppLC {

	public static void main(String[] args) {
		String serverHost = "127.0.0.1";
		int serverPort = 20716;
		
		Client client = new Client(serverHost, serverPort);
	}

}
