package utils;

import java.io.*;
import java.util.Base64;

public class SerializationUtility {

    /** Read the object from Base64 string. */
    public static Object fromString( String s ) {
        byte [] data = Base64.getDecoder().decode( s );
        ObjectInputStream ois = null;
        Object o = null;
        try {
            ois = new ObjectInputStream(
                    new ByteArrayInputStream(  data ) );
            o  = ois.readObject();
            ois.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return o;
    }

    /** Write the object to a Base64 string. */
    public static String serialize(Serializable o ) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream( baos );
            oos.writeObject( o );
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
