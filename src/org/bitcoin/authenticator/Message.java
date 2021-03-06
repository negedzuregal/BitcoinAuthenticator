package org.bitcoin.authenticator;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.simple.JSONObject;

/**
 * This class handles the communication messages sent between the Authenticator and the wallet.
 */
public class Message {
	
	static DataInputStream in;
	static DataOutputStream out ;
	
	/**Constructor takes in active Connection object to the wallet*/
	public Message(Connection conn) throws IOException{
		in = conn.getInputStream();
		out = conn.getOutputStream();
	}
	
	@SuppressWarnings("unchecked")
	public void sentRequestID(String requestID) throws IOException{
		JSONObject jo = new JSONObject();
		jo.put("requestID", requestID);
		byte[] payload = jo.toString().getBytes();
		out.writeInt(payload.length);
		out.write(payload);
	}
	
	/**
	 * Method to receive a transaction from the wallet. It decrypts the message and verifies the HMAC.
	 * Returns a TxData object containing the number of inputs, child key indexes, public keys from the wallet, and 
	 * raw unsigned transaction.
	 */
	public TxData receiveTX(SecretKey sharedsecret) throws Exception {
		//Receive the encrypted payload
	  	byte[] cipherBytes;
	  	int size = in.readInt();
		cipherBytes = new byte[size];
		in.read(cipherBytes);
		//Decrypt the payload
	  	Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
	    cipher.init(Cipher.DECRYPT_MODE, sharedsecret);
	    //Split the payload into it's parts.
	    String payload = Utils.bytesToHex(cipher.doFinal(cipherBytes));
		byte[] testpayload = Utils.hexStringToByteArray(payload.substring(0,payload.length()-64));
		byte[] hash = Utils.hexStringToByteArray(payload.substring(payload.length()-64,payload.length()));
	    TxData data = new TxData(testpayload);
	    //Verify the HMAC
	    Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(sharedsecret);
		byte[] macbytes = mac.doFinal(testpayload);
		if (Arrays.equals(macbytes, hash)){
			//Return the payload
			return data;
		}
		else {
			System.out.println("Message authentication code is invalid");
			return null;
		}
	}

	/**
	 * Method to send the transaction signature back to the wallet.
	 * It calculates the HMAC of the signature, concatentates it, and encypts it with AES.
	 */
	public void sendEncrypted (byte[] sig, SecretKey sharedsecret) throws IOException, InvalidKeyException, NoSuchAlgorithmException{
		//Calculate the HMAC
    	Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(sharedsecret);
		byte[] macbytes = mac.doFinal(sig);
		//Concatenate it with the signature
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
		outputStream.write(sig);
		outputStream.write(macbytes);
		byte payload[] = outputStream.toByteArray();
  		//Encrypt the payload
  	    Cipher cipher = null;
		try {cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");} 
		catch (NoSuchAlgorithmException e) {e.printStackTrace();} 
		catch (NoSuchPaddingException e) {e.printStackTrace();}
        try {cipher.init(Cipher.ENCRYPT_MODE, sharedsecret);} 
        catch (InvalidKeyException e) {e.printStackTrace();}
        byte[] cipherBytes = null;
		try {cipherBytes = cipher.doFinal(payload);} 
		catch (IllegalBlockSizeException e) {e.printStackTrace();} 
		catch (BadPaddingException e) {e.printStackTrace();}
		//Send the payload over to the wallet
	  	out.writeInt(cipherBytes.length);
		out.write(cipherBytes);
    }
	
}
