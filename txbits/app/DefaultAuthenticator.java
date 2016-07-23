import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * Authenticator which keeps credentials to be passed to the requestor based on authority of the requesting URL. The
 * authority is <pre>user:password@host:port</pre>, where all parts are optional except the host.
 * <p>
 * If the configured credentials are not found, the Authenticator will use the credentials embedded in the URL, if
 * present. Embedded credentials are in the form of <pre>user:password@host:port</pre>
 *   
 * @author Michael Fortin 2011-09-23
 */
public final class DefaultAuthenticator extends Authenticator {

    private static final Logger LOG = Logger.getLogger(DefaultAuthenticator.class.getName());
    private static DefaultAuthenticator instance;

    private Map<String, PasswordAuthentication> authInfo = new HashMap<String, PasswordAuthentication>();

    private DefaultAuthenticator() {
    }

    public static synchronized DefaultAuthenticator getInstance() {
        if (instance == null) {
            instance = new DefaultAuthenticator();
            Authenticator.setDefault(instance);
        }
        return instance;
    }

    // unit testing
    static void reset() {
        instance = null;
        Authenticator.setDefault(null);        
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {

        String requestorInfo = getRequestorInfo();
        LOG.info(getRequestorType() + " at \"" + getRequestingPrompt() + "\" is requesting " + getRequestingScheme()
                + " password authentication for \"" + requestorInfo + "\"");

        if (authInfo.containsKey(requestorInfo)) {
            return authInfo.get(requestorInfo);
        } else {
            PasswordAuthentication pa = getEmbeddedCredentials(getRequestingURL());
            if (pa == null) {
                LOG.warning("No authentication information");
            }
            return pa;
        }

    }

    /**
     * Register the authentication information for a given URL.
     * 
     * @param url - the URL that will request authorization
     * @param auth - the {@link PasswordAuthentication} for this URL providing the credentials
     */
    public void register(URL url, PasswordAuthentication auth) {
        String requestorInfo = getRequestorInfo(url.getHost(), url.getPort()); 
        authInfo.put(requestorInfo, auth);
    }

    /**
     * Get the requestor info based on info provided.
     * 
     * @param host - hostname of requestor
     * @param port - TCP/IP port
     * @return requestor info string
     */
    private String getRequestorInfo(String host, int port) {

        String fullHostname;
        try {
            InetAddress addr = InetAddress.getByName(host);
            fullHostname = addr.getCanonicalHostName();
        } catch (UnknownHostException e) {
            fullHostname = host;
        }

        if (port == -1) {
            return fullHostname;
        } else {
            return fullHostname + ":" + port;
        }
    }

    /**
     * Get the requestor info for the request currently being processed by this Authenticator.
     * 
     * @return requestor info string for current request
     */
    private String getRequestorInfo() {

        String host;
        InetAddress addr = getRequestingSite();
        if (addr == null) {
            host = getRequestingHost();
        } else {
            host = addr.getCanonicalHostName();
        }
        return getRequestorInfo(host, getRequestingPort());
    }

    /**
     * Get the credentials from the requesting URL.
     * 
     * @param url - URL to get the credentials from (can be null, method will return null)
     * @return PasswordAuthentication with credentials from URL or null if URL contains no credentials or if URL is
     * null itself
     */
    PasswordAuthentication getEmbeddedCredentials(URL url) {

        if (url == null) {
            return null;
        }

        String userInfo = url.getUserInfo();
        int colon = userInfo == null ? -1 : userInfo.indexOf(":");
        if (colon == -1) {
            return null;
        } else {
            String userName = userInfo.substring(0, colon);
            String pass = userInfo.substring(colon + 1);
            return new PasswordAuthentication(userName, pass.toCharArray());
        }
    }
}
