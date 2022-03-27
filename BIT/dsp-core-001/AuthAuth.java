package org.stopbadware.dsp.sec;

import java.net.URI;

import javax.ws.rs.core.HttpHeaders;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SessionStorageEvaluator;
import org.apache.shiro.mgt.SessionsSecurityManager;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stopbadware.dsp.data.SecurityDbHandler;

/**
 * Authentication and authorization handler 
 */
public abstract class AuthAuth {
	
	private static Realm realm = new Realm();
	private static SecurityManager securityManager = new DefaultSecurityManager(realm);
	private static final long MAX_AGE = (System.getenv("MAX_AUTH_AGE") != null) ? Long.valueOf(System.getenv("MAX_AUTH_AGE")) : 180L;
	private static final Logger LOG = LoggerFactory.getLogger(AuthAuth.class);

    private static final String ADMIN_NAME = "devops sbw";
	
	static {
		SecurityUtils.setSecurityManager(securityManager);
		((DefaultSessionManager)((SessionsSecurityManager) securityManager).getSessionManager()).setSessionValidationSchedulerEnabled(false);
		SessionStorageEvaluator sessionDAO = ((DefaultSubjectDAO)((DefaultSecurityManager) securityManager).getSubjectDAO()).getSessionStorageEvaluator();
		((DefaultSessionStorageEvaluator)sessionDAO).setSessionStorageEnabled(false);
	}

	/**
	 * Creates and returns a subject from the provided parameters
	 * @param httpHeaders HTTP Header information that should include
	 * "SBW-Key", "SBW-Signature", and "SBW-Timestamp" - otherwise
	 * a warning will be logged and null returned
	 * @param uri destination URI of the request
	 * @return an authenticated Subject for use in authorization and 
	 * authentication checks, or null if authentication failed
	 */
	public static Subject getSubject(HttpHeaders httpHeaders, URI uri) {
		String path = (uri.getPath() != null) ? uri.getPath().toString() : "";
		String key = "UNKNOWN";
		String sig = null;
		long ts = 0L;
		try {
			key = httpHeaders.getRequestHeaders().getFirst("SBW-Key");
			sig = httpHeaders.getRequestHeaders().getFirst("SBW-Signature");
			ts = Long.valueOf(httpHeaders.getRequestHeaders().getFirst("SBW-Timestamp"));
		} catch (NullPointerException | IllegalStateException | NumberFormatException e) {
			LOG.warn("Exception thrown parsing headers:\t{}", e.toString());
		}
		
		LOG.info("{} accessing '{}'", key, path);
		Subject subject = SecurityUtils.getSubject();
		subject.logout();
		
		boolean validKey = keyIsValid(key);
		boolean validSig = sigIsValid(sig);
		boolean validTs = tsIsValid(ts);
		if (validKey && validSig && validTs) {
			RestToken token = new RestToken(key, sig, path, ts);
			try {
				subject.login(token);
			} catch (AuthenticationException e) {
				LOG.warn("Authentication failure for '{}': {}", token.getPrincipal(), e.getMessage());
			} catch (Exception e) {
				LOG.warn("Exception thrown authenticating '{}': {}", token.getPrincipal(), e.getMessage());
			} 
		} else {
			LOG.warn("Authentication failure for '{}' - valid key: {}\tvalid signature: {}\tvalid timestamp: {}", key, validKey, validSig, validTs);
		}
		
		return (subject.isAuthenticated()) ? subject : null;
	}
	
	private static boolean keyIsValid(String key) {
		return (key != null && key.length() > 0);
	}
	
	private static boolean sigIsValid(String sig) {
		return (sig != null && sig.length() > 0);
	}
	
	private static boolean tsIsValid(long ts) {
		long age = (System.currentTimeMillis()/1000) - ts;
		return age < MAX_AGE;
	}
	
	/**
	 * Checks if the provided subject is associated with a specific participant
	 * @param subject the Shiro Subject to check
	 * @param participant case insensitive prefix of the participant
	 * @return true if, and only if, the account is associated with the participant
	 */
	public static boolean subjectIsMemberOf(Subject subject, String participant) {
		return new SecurityDbHandler().getParticipant(subject.getPrincipal().toString()).equalsIgnoreCase(participant);
	}
	
	
	/**
	 * Increments an account's rate limit counter and checks if the account is rate limited
	 * @param subject the Subject to update and check
	 * @return true if the account has accessed rate limited resources at a frequency
	 * exceeding the rate limit
	 */
	public static boolean isRateLimited(Subject subject) {
		if (subject.hasRole(Role.RATELIMIT_WHITELISTED.toString())) {
			return false;
		} else {
			return new SecurityDbHandler().isRateLimited(subject.getPrincipal().toString());
		}
	}

    public static Subject createSystemSubject() {
        PrincipalCollection principals = new SimplePrincipalCollection(ADMIN_NAME, realm.getName());
        Subject subject = new Subject.Builder(securityManager).buildSubject();
        return subject;
    }
}
