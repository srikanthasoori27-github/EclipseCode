/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Base64;

public class BasicAuthRequestFilter implements ClientRequestFilter {

    private String _userName;
    private String _password;

    BasicAuthRequestFilter(String userName, String password) {
        _userName = userName;
        _password = password;
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext) {
        MultivaluedMap<String, Object> headers = clientRequestContext.getHeaders();
        if (headers != null) {
            String userPass = _userName + ":" + _password;
            String encoded = Base64.getEncoder().encodeToString(userPass.getBytes());
            String header = "Basic " + encoded;
            headers.add(HttpHeaders.AUTHORIZATION, header);
        }
    }
}
