package sailpoint.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemoteLoginToken;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

/**
 * Service to allow creating of remote login tokens
 * to allow psuedo-sso for integrations that want to
 * launch into our app given some user context.
 */
public class RemoteLoginService {

    private SailPointContext context;

    /**
     * Constructor
     * @param context SailPointContext
     * @throws InvalidParameterException
     */
    public RemoteLoginService(SailPointContext context) throws InvalidParameterException {
        if (null == context) {
            throw new InvalidParameterException("context");
        }
        this.context = context;
    }

    /**
     * creates and returns remote login token for given identity.
     * @param identityName  identity cube name.
     * @param remoteHost    host name requesting remote login(can be null).
     * @return remote login token
     * @throws GeneralException
     */
    public String createRemoteLoginToken(String identityName, String remoteHost)
        throws GeneralException {

        return generateRemoteToken(identityName, remoteHost);
    }

    /**
     * Returns the identity name, and its remote login token
     * @param appName         filter to find Identity cube name.
     * @param nativeIdentity  filter to find Identity cube name.
     * @param remoteHost      host name requesting remote login(can be null).
     * @return A Map with details identity cube name and its remote login token.
     * @throws GeneralException
     */
    public Map<String, String> createRemoteLoginToken(String appName, String nativeIdentity, String remoteHost)
        throws GeneralException {

        // get identity name using link
        Filter filter = Filter.and(Filter.eq("application.name", appName),
        Filter.eq("nativeIdentity", nativeIdentity));
        Link uniqueLink = context.getUniqueObject(Link.class, filter);
        if ( null == uniqueLink ) {
            throw new GeneralException("Unable to find identity");
        }

        String identityName = uniqueLink.getIdentity().getName();
        String tokenId = generateRemoteToken(identityName, remoteHost);

        Map<String, String> result = new HashMap<String, String>();
        result.put("identityName", identityName);
        result.put("remoteLoginToken", tokenId);

        return result;
    }

    /**
     * creates and returns remote login token for given identity.
     * @param identityName  identity name.
     * @param remoteHost    host name requesting remote login(can be null).
     * @return remote login token
     * @throws GeneralException
     */
    private String generateRemoteToken(String identityName, String remoteHost)
        throws GeneralException {

        String admin = context.getUserName();

        // make sure the token is being request for a valid user
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("name", identityName));
        if ( ( context.countObjects(Identity.class, ops) ) != 1 ) {
            throw new GeneralException("Error creating login token, identity '" +identityName+"' was not found.");
        }

        // Default to 2 minutes, but allow configuration override
        int secondsToExpire = 864000;
        Configuration config = context.getConfiguration();
        if ( config != null ) {
            // This is expected to be expressed in seconds
            int secs = config.getInt(Configuration.REMOTE_LOGIN_TOKEN_MAX_AGE);
            if ( secs > 0 )
                secondsToExpire = secs;
        }

        // Build a RemoteLoginToken that expires relative to now
        Date expiration =  Util.incrementDateBySeconds(new Date(), secondsToExpire);
        RemoteLoginToken token = new RemoteLoginToken(admin,
                                                      expiration,
                                                      identityName,
                                                      remoteHost);
        // Persist the token so it can be used during authentication
        context.saveObject(token);
        context.commitTransaction();

        // The hibernate ID becomes the token ID
        String tokenId = token.getId();
        if ( tokenId == null )
            throw new GeneralException("Error building new RemoteLoginToken Id was null after commit.");

        return tokenId;
    }
}
