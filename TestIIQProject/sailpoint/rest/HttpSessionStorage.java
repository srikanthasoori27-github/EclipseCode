/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import javax.servlet.http.HttpSession;

import sailpoint.service.SessionStorage;

/**
 * A session storage implementation that is backed by the HttpSession.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class HttpSessionStorage implements SessionStorage {

    private HttpSession httpSession;

    /**
     * Constructs a new instance of HttpSessionStorage.
     *
     * @param httpSession The HttpSession instance.
     */
    public HttpSessionStorage(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(String key, Object value) {
        httpSession.setAttribute(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(String key) {
        return httpSession.getAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(String key) {
        return httpSession.getAttribute(key) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(String key) {
        httpSession.removeAttribute(key);
    }
    
    protected HttpSession getHttpSession() {
        return httpSession;
    }

}