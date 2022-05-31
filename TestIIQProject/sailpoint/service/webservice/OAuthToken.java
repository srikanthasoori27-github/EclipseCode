/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.webservice;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * An OAuthToken holds the access token and expiry time
 */
public class OAuthToken {

    /**
     * the access token
     */
    private String accessToken;

    /**
     * how many seconds until the token expires
     * (relative to the time it was first created)
     */
    private int expiresIn;

    /**
     * The absolute time at which the token will expire
     * (this is calculated by constructor)
     */
    private Date expiryDate;

    public OAuthToken(String accessToken, int expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;

        // Expires in expiresIn seconds
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, expiresIn);
        expiryDate = calendar.getTime();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public String toString() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = format.format(expiryDate);

        String res= "accessToken:" + accessToken + ", expiresIn:" + expiresIn + ", expiryDate:" + dateString;
        return res;
    }

}
