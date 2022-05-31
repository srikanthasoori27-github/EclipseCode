/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.io.IOException;
import java.util.List;

/**
 * Abstracts http redirect method
 */
public interface Redirectable {
    void redirect(String sessionKey, String redirectUrl, List<String> paramsToFilter, String preRedirectUrl) throws IOException;
}