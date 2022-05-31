/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

/**
 * An implementation of AcesssTokenProvider knows how to return an access token
 */
public interface AccessTokenProvider {
        String getAccessToken();
}

