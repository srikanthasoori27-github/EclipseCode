/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.credential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.pam.credential.Configuration;
import sailpoint.pam.credential.CredentialManager;
import sailpoint.pam.credential.CredentialManagerFactory;
import sailpoint.pam.credential.Request;
import sailpoint.pam.credential.Response;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

/**
 * This class "retrieves" credentials based on an application name. Internally, it finds all
 * CredentialSource configuration objects related to an application, iterates through a list of CredentialAssociation
 * configuration objects, creates requests to the CredentialManager API and puts the retrieved values onto the
 * application configuration attributes map.
 */
public class CredentialRetriever {
    private SailPointContext context = null;
    private List<CredentialSource> sources = null;

    private static final Log log = LogFactory.getLog(CredentialRetriever.class);
    private static final String CREDENTIAL_CONFIG = "CredentialConfiguration";

    public CredentialRetriever(SailPointContext context) {
        this.context = context;
    }

    /**
     * The main public method responsible for updating credentials on the Application. If no Configuration object
     * exists this method is a no-op and will not fail or throw exceptions.
     * @param app update the credentials on this Application object
     * @throws GeneralException if the CredentialAssociation attribute name doesn't resolve to a valid MapUtil path.
     * @see sailpoint.tools.MapUtil#put(java.util.Map, String, Object)
     */
    public void updateCredentials(Application app) throws GeneralException {
        if (app == null) {
            return;
        }
        
        String appName = app.getName();
        Attributes<String, Object> atts = app.getAttributes();
        
        // filters list of sources by application name
        List<CredentialSource> sources = filterCredentialSources(appName);
        for (CredentialSource source : Util.safeIterable(sources)) {
            decryptEncryptedFields(source.getAttributes());
            sailpoint.pam.credential.Configuration config = createConfig(source);
            CredentialManagerFactory factory = new CredentialManagerFactory(config);
            CredentialManager credMan = factory.getCredentialManager();
            if (credMan != null) {
                for (CredentialAssociation assoc : Util.safeIterable(source.getCredentialAssociations())) {
                    decryptEncryptedFields(assoc.getAttributes());
                    Request request = createRequest(assoc, config);
                    Response response = credMan.getCredential(request);

                    String value = response.getString(request.getAttributeName());
                    if (value != null) {
                        MapUtil.put(atts, assoc.getAttributeName(), value);
                    } else {
                        if (log.isInfoEnabled()) {
                            log.info("association: " + assoc.getAttributeName() + " value was null");
                        }
                    }
                }
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("credential manager was not found for source: " + source.getName());
                }
            }
        }
    }

    /**
     * Determine if a Credential Configuration exists for a given Application. The Application
     * must have a name set otherwise this method always returns false.
     * @param app Application to check if a configuration exists
     * @return true/false if one or more configurations exist for the application.
     */
    public boolean hasConfiguration(Application app) {
        if (app == null) {
            return false;
        }
        
        String appName = app.getName();
        for (CredentialSource cs : Util.safeIterable(getSources())) {
            for (CredentialAssociation ca : Util.safeIterable(cs.getCredentialAssociations())) {
                if (Util.nullSafeEq(appName, ca.getApplicationName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method to filter credential sources by application name credential associations
     * @param applicationName Application name to filter on
     * @return a non-null subset of configured CredentialSources
     */
    protected List<CredentialSource> filterCredentialSources(String applicationName) throws GeneralException {
        List<CredentialSource> sourceList = new ArrayList<CredentialSource>();
        // get the list of credential sources from the configuration
        List<CredentialSource> unfilteredSources = getSources();
        for (CredentialSource cs : Util.safeIterable(unfilteredSources)) {
            List<CredentialAssociation> assocList = new ArrayList<>();
            // get all the credential associations for the source
            for (CredentialAssociation ca : Util.safeIterable(cs.getCredentialAssociations())) {
                if (Util.nullSafeEq(ca.getApplicationName(), applicationName)) {
                    if (log.isDebugEnabled()) {
                        log.debug("adding " + ca.getApplicationName() + " " + ca.getAttributeName() + " to credential source");
                    }
                    assocList.add(ca);
                }
            }
            if (!assocList.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("adding associations to credential source: " + cs.getName());
                }
                // IIQCB-2010 - deepCopy ensures we don't modify the existing Configuration object and possibly
                //              save a filtered Configuration object
                CredentialSource newSource = (CredentialSource)cs.deepCopy(context);
                newSource.setCredentialAssociations(assocList);
                sourceList.add(newSource);
            }
        }
        return sourceList;
    }

    /**
     * Returns a non-null Configuration based on a CredentialSource
     * @param source CredentialSource to create configuration
     * @return pam credential Configuration object
     */
    protected Configuration createConfig(CredentialSource source) {
        Configuration config = new Configuration();
        if (source != null) {
            config.setCredentialManager(source.getCredentialClass());
            config.setConfiguration(source.getAttributes());
        }
        return config;
    }

    /**
     * Returns a non-null Request based on a CredentialAssociation
     * @param assoc CredentialAssociation object to create request
     * @return pam credential Request object
     */
    protected Request createRequest(CredentialAssociation assoc, Configuration configuration) {
        Request req = new Request(configuration);
        if (assoc != null) {
            req.setAttributeName(assoc.getCredentialAttributeName());
            req.putAll(assoc.getAttributes());
        }
        return req;
    }

    /**
     * Returns list of CredentialSource objects
     * @return non-null list of CredentialSource objects
     */
    @SuppressWarnings("unchecked")
    protected List<CredentialSource> getSources() {
        if (this.sources == null) {
            try {
                sailpoint.object.Configuration config = this.context.getObjectByName(sailpoint.object.Configuration.class, CREDENTIAL_CONFIG);
                if (config != null) {
                    this.sources = (List<CredentialSource>)config.get("sources");
                }
            } catch (GeneralException e) {
                // bad things happened, return null configuration
                log.error("An unknown exception occurred retreiving the configuration, credential cycling is not in use.", e);
            }
        }
        // exception or sources not found, return empty sources list
        if (this.sources == null) {
            this.sources = new ArrayList<CredentialSource>();
        }
        return this.sources;
    }

    @Untraced
    private void decryptEncryptedFields(Map<String, Object> attributes) {
        if(attributes == null) {
            return;
        }

        for(String key : attributes.keySet()) {
            Object value = null;

            try {
                value = decryptValue(attributes.get(key));
            } catch (GeneralException ex) {
                log.warn("Unable to decrypt value for key " + key);
            }

            if(value != null) {
                attributes.put(key, value);
            }
        }
    }
    
    @Untraced
    private void decryptEncryptedFields(List attributes) throws GeneralException {
        for(Object value : attributes) {
            decryptValue(value);
        }
    }

    @Untraced
    private String decryptValue(Object value) throws GeneralException {
        if (value instanceof String) {
            return context.decrypt((String) value);
        } else if (value instanceof Map) {
            decryptEncryptedFields((Map) value);
        } else if (value instanceof List) {
            decryptEncryptedFields((List) value);
        }

        return null;
    }
}