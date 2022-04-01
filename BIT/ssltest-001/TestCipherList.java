package de.dogcraft.ssltest.tests;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;

import org.bouncycastle.crypto.tls.CipherSuite;
import org.bouncycastle.crypto.tls.CompressionMethod;
import org.bouncycastle.crypto.tls.TlsCipher;
import org.bouncycastle.crypto.tls.TlsCompression;
import org.bouncycastle.crypto.tls.TlsKeyExchange;

import de.dogcraft.ssltest.tests.TestingTLSClient.TLSCipherInfo;
import de.dogcraft.ssltest.utils.CipherProbingClient;

public class TestCipherList {

    private final String host;

    private final int port;

    private Vector<Integer> ciphers = new Vector<>();

    private boolean serverPref = false;

    private static HashMap<Integer, String> cipherNames = new HashMap<>();
    static {
        initCipherNames();
    }

    public TestCipherList(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private static void initCipherNames() {
        Field[] fs = CipherSuite.class.getFields();
        for (Field field : fs) {
            try {
                cipherNames.put(field.getInt(null), field.getName());
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

    public static Collection<Integer> getAllCiphers() {
        return cipherNames.keySet();
    }

    public Collection<Integer> getCiphers() {
        return ciphers;
    }

    public boolean hasServerPref() {
        return serverPref;
    }

    public String[] determineCiphers(TestOutput pw) {
        LinkedList<TestResultCipher> yourCiphers = new LinkedList<>();
        Collection<Integer> ciphers = getAllCiphers();

        try {
            for (int n = 0; n < ciphers.size(); n++) {
                TestResultCipher selection = choose(ciphers);
                yourCiphers.add(selection);

                selection.priority = n;

                String cipherDesc = selection.toString();

                if (pw != null) {
                    pw.output(cipherDesc);
                }

                ciphers.remove(selection.cipherID);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        int best = yourCiphers.get(0).getCipherID();
        int worst = yourCiphers.get(yourCiphers.size() - 1).getCipherID();

        int choice;
        try {
            choice = choose(Arrays.asList(worst, best)).getCipherID();
        } catch (IOException e) {
            choice = worst;
            e.printStackTrace();
        }

        serverPref = choice != worst;

        // TODO output was already made to the test output;
        // return chosen.toArray(new TestResultCipher[chosen.size()]);

        return new String[0];
    }

    public class TestResultCipher {

        public Integer cipherID;

        public Boolean supported;

        public Integer priority;

        public TlsKeyExchange kex;

        public TlsCompression compress;

        public TlsCipher cipher;

        public TLSCipherInfo info;

        public Integer getCipherID() {
            return cipherID;
        }

        public String getCipherName() {
            return TestCipherList.cipherNames.get(cipherID);
        }

        public Boolean getSupported() {
            return supported;
        }

        public Integer getPriority() {
            return priority;
        }

        protected void setCipherID(Integer cipherID) {
            this.cipherID = cipherID;
        }

        protected void setSupported(Boolean supported) {
            this.supported = supported;
        }

        protected void setPriority(Integer priority) {
            this.priority = priority;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            Formatter f = new Formatter(sb);
            f.format("%06x (%s): kex=%s(%d), auth=%s(%d), enc=%s(%d) mode=%s mac=%s(%d) pfs=%s", //
                    getCipherID(), getCipherName(), //
                    info.getKexType(), info.getKexSize(), //
                    info.getAuthKeyType(), info.getAuthKeySize(), //
                    info.getCipherType(), info.getCipherSize(), info.getCipherMode(), //
                    info.getMacType(), info.getMacSize(), //
                    info.isPFS() ? "yes" : "no");
            return sb.toString();
        }
    }

    private TestResultCipher choose(final Collection<Integer> ciphers) throws IOException {
        Socket sock = new Socket(host, port);
        TestingTLSClient tcp = new TestingTLSClient(sock.getInputStream(), sock.getOutputStream());
        CipherProbingClient tc = new CipherProbingClient(host, port, ciphers, new short[] { CompressionMethod._null }, null);
        try {
            tcp.connect(tc);
            sock.getOutputStream().flush();
            tcp.close();
            sock.close();
        } catch (IOException e) {

        }

        int selectedCipherSuite = tc.getSelectedCipherSuite();
        if (selectedCipherSuite == 0) {
            throw new IOException();
        }

        if (tc.isFailed() || tcp.hasFailedLocaly()) {
            System.out.println("--- failed ---: " + cipherNames.get(selectedCipherSuite));
        }

        TestResultCipher resultCipher = new TestResultCipher();
        resultCipher.cipherID = selectedCipherSuite;
        resultCipher.priority = 0;
        resultCipher.supported = true;
        resultCipher.kex = tc.getKeyExchange();
        resultCipher.compress = tc.getCompression();
        // resultCipher.cipher = tc.getCipher();
        resultCipher.info = tcp.getCipherInfo();

        return resultCipher;
    }

}
