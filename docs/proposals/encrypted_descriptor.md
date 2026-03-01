// EncryptedMultisigDescriptor.java

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.AEADBadTagException;

public class EncryptedMultisigDescriptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    public static byte[] getPairKey(String zpub1, String zpub2) throws Exception {
        if (zpub1 == null || zpub2 == null || zpub1.isEmpty() || zpub2.isEmpty()) {
            throw new IllegalArgumentException("zpubs cannot be null or empty");
        }
        String[] pair = {zpub1, zpub2};
        Arrays.sort(pair);
        String concat = pair[0] + pair[1];
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(concat.getBytes(StandardCharsets.UTF_8));
    }

    public static String encrypt(String descriptor, byte[] key) throws Exception {
        SecretKeySpec skey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, skey, spec);
        byte[] ct = cipher.doFinal(descriptor.getBytes(StandardCharsets.UTF_8));

        byte[] blob = new byte[IV_LENGTH + ct.length];
        System.arraycopy(iv, 0, blob, 0, IV_LENGTH);
        System.arraycopy(ct, 0, blob, IV_LENGTH, ct.length);

        return Base64.getEncoder().encodeToString(blob);
    }

    public static String decrypt(String blobStr, byte[] key) throws Exception {
        byte[] blob = Base64.getDecoder().decode(blobStr);
        if (blob.length < IV_LENGTH) throw new IllegalArgumentException("Invalid blob length");

        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
        byte[] ct = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);

        SecretKeySpec skey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, skey, spec);

        try {
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            return null;
        }
    }

    public static List<String[]> generatePairs(String[] zpubs) {
        if (zpubs.length != 3) {
            throw new IllegalArgumentException("Must provide exactly 3 zpubs");
        }

        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{zpubs[0], zpubs[1]});
        pairs.add(new String[]{zpubs[0], zpubs[2]});
        pairs.add(new String[]{zpubs[1], zpubs[2]});

        return pairs;
    }

    public static List<String> encrypt2of3(String descriptor, String[] zpubs) throws Exception {
        List<String[]> pairs = generatePairs(zpubs);
        List<String> blobs = new ArrayList<>();

        for (String[] pair : pairs) {
            byte[] key = getPairKey(pair[0], pair[1]);
            String blob = encrypt(descriptor, key);
            blobs.add(blob);
        }

        return blobs;
    }

    public static String decrypt2of3(String blob, String[] zpubs) throws Exception {
        List<String[]> pairs = generatePairs(zpubs);

        for (String[] pair : pairs) {
            byte[] key = getPairKey(pair[0], pair[1]);
            String descriptor = decrypt(blob, key);
            if (descriptor != null) {
                return descriptor;
            }
        }

        throw new SecurityException("Failed to decrypt blob with given zpubs");
    }
}
