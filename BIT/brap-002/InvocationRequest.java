package no.tornado.brap.common;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * The request is created by the <code>MethodInvocationHandler</code>
 * when it intercepts method calls.
 *
 * This object is then serialized using standard Java binary serialization and
 * transported in the HTTP request body to the server.
 */
public class InvocationRequest implements Serializable {
    /**
     * The methodName to invoke on the remote service.
     */
    private String methodName;

    /**
     * The type classes of the parameters if any
     */
    private Class[] parameterTypes;

    /**
     * The parameters to the method call.
     */
    private Object[] parameters;

    /**
     * An optional object holding credentials to be processed by the <code>AuthenticationProvider</code>
     * and <code>AuthorizationProvider</code> on the server.
     */
    private Serializable credentials;

    public InvocationRequest(Method method, Object[] parameters, Serializable credentials) {
        this.credentials = credentials;
        this.methodName = method.getName();
        this.parameterTypes = method.getParameterTypes();
        this.parameters = parameters;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    public Serializable getCredentials() {
        return credentials;
    }

    public void setCredentials(Serializable credentials) {
        this.credentials = credentials;
    }
}
