/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Package decl.
 */
package sailpoint.rest;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Notary;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.IdentityMatchAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.integration.Util;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.Configuration;
import sailpoint.object.ESignatureType;
import sailpoint.object.Identity;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * ElectronicSignatureResource.
 *
 * The resource which handles electronic signatures.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@Path("electronicSignatures")
public class ElectronicSignatureResource extends BaseResource
{
    private static final String KEY_NAME = "name";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_MEANINGS = "meanings";
    
    /**
    * Type of object being electronically signed
    */
    public enum SignatureObjectType {
    	CERTICATION_TYPE("certification"),
    	WORKITEM_TYPE("workitem");
    	
    	private final String type;
    	
    	private SignatureObjectType(final String type) {
    		this.type=type;
    	}
    	
    	@Override
    	public String toString() {
    		return this.type;
    	}
    };

    private static Log log = LogFactory.getLog(ElectronicSignatureResource.class);

    private Configuration _config;
    
    /**
     * Gets all of the configured electronic signature meanings.
     *
     * @return The meanings list result.
     */
    @GET
    @Path("meanings")
    public ListResult getMeanings() throws GeneralException
    {
        //We use this in system setup and certification definition
        authorize(CompoundAuthorizer.or(new RightAuthorizer("FullAccessCertifications"), 
                new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR)));
        
        List<Map> meanings = new ArrayList<Map>();

        List<ESignatureType> signatureTypes = getElectronicSignatureTypes();
        if (null != signatureTypes) {
            for (ESignatureType signatureType : signatureTypes) {
                Map<String, Object> meaning = new HashMap<String, Object>();
                meaning.put(KEY_NAME, signatureType.getName());
                meaning.put(KEY_DISPLAY_NAME, signatureType.getDisplayableName());
                Map<String, String> localMeanings = signatureType.getMeanings();
                for (String key : localMeanings.keySet()) {
                    String sanitizedMeaning = WebUtil.sanitizeHTML(localMeanings.get(key));
                    localMeanings.put(key, sanitizedMeaning);
                }
                meaning.put(KEY_MEANINGS, localMeanings);
                
                if(meaning.get(KEY_MEANINGS) == null) {
                    meaning.put(KEY_MEANINGS, new HashMap<String, String>());
                }

                meanings.add(meaning);
            }
        }

        return new ListResult(meanings, meanings.size());
    }

    
    /**
     * 
     * Get the localized meaning for a given Signature type.  This is called
     * from the GWE when displaying the available meanings that can be selected
     * for an approval step.
     * 
     * @param name
     * @return
     * @throws GeneralException
     */    
    @GET
    @Path("meanings/{name}")
    public ObjectResult getMeaning(@PathParam("name") String name ) throws GeneralException {
        // this is only used in the system setup right now so auth as system administrator
        authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));
        ObjectResult result = new ObjectResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        name = decodeRestUriComponent(name);
        
        List<ESignatureType> signatureTypes = getElectronicSignatureTypes();
        if (null != signatureTypes) {
            Iterator<ESignatureType> itr = signatureTypes.iterator();
            while (itr.hasNext()) {
                ESignatureType signatureType = itr.next();
                String signatureTypeName = signatureType.getName();
                if (Util.nullSafeCompareTo(signatureTypeName, name) == 0 ) {
                    Notary notary = new Notary(this.getContext(), getLocale());
                    String meaningText = notary.getLocalizedMeaning(signatureTypeName);
                    Map<String,Object> meta = new HashMap<String,Object>();
                    meta.put("meaning", meaningText);
                    result.setMetaData(meta);
                    result.setObject(meaningText);
                    result.setStatus(RequestResult.STATUS_SUCCESS);
                    break;
                }
            }
        }
        return result;
    }
    
    @PUT
    @Path("meanings/{name}")
    public RequestResult updateMeaning(@PathParam("name") String name, @FormParam("json") String json)
            throws GeneralException {

        // this is only used in the system setup right now so auth as system administrator
        authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));
        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        name = decodeRestUriComponent(name);
        
        List<ESignatureType> signatureTypes = getElectronicSignatureTypes();
        if (null != signatureTypes) {
            Iterator<ESignatureType> itr = signatureTypes.iterator();
            while (itr.hasNext()) {
                ESignatureType signatureType = itr.next();
                if (name.equals(signatureType.getName())) {
                    try {
                        // json contains both Strings and a Map, so no generics... :(
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meaningMap = JsonHelper.mapFromJson(String.class, Object.class, json);
                        
                        signatureType.setName((String)meaningMap.get("name"));
                        signatureType.setDisplayName((String)meaningMap.get("displayName"));
                        
                        // Strip out any dangerous HTML, since we don't know where this came from
                        @SuppressWarnings("unchecked")
                        HashMap<String, String> mmap = (HashMap<String, String>)meaningMap.get("meanings");
                        Iterator<Entry<String, String>> it = mmap.entrySet().iterator();
                        while (it.hasNext()) {
                            Entry<String, String> pairs = it.next();
                            pairs.setValue(WebUtil.safeHTML(pairs.getValue()));
                        }
                        signatureType.setMeanings(mmap);

                        getContext().saveObject(getConfiguration());
                        getContext().commitTransaction();

                        result.setStatus(RequestResult.STATUS_SUCCESS);
                    }
                    catch (GeneralException ex) {
                        if (log.isErrorEnabled()) {
                            log.error(ex.getMessage(), ex);
                        }
                        ArrayList<String> errors = new ArrayList<String>(1);
                        errors.add(ex.getLocalizedMessage());
                        result.setErrors(errors);
                    }

                    break;
                }
            }
        }
        
        return result;
    }
    
    @POST
    @Path("meanings")
    public RequestResult createMeaning(@FormParam("name") String newName, @FormParam("json") String json)
            throws GeneralException {

        // this is only used in the system setup right now so auth as system administrator
        authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));
        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        List<ESignatureType> signatureTypes = getElectronicSignatureTypes();
        if (null != signatureTypes) {
            try {
                // json contains both Strings and a Map, so no generics... :(
                Map<String, Object> meaningMap = JsonHelper.mapFromJson(String.class, Object.class, json);

                ESignatureType newType = new ESignatureType();
                newType.setName((String)meaningMap.get("name"));
                if(meaningMap.get("displayName") != null && (String)meaningMap.get("displayName") != "") {
                    newType.setDisplayName((String)meaningMap.get("displayName"));
                }
                else {
                    newType.setDisplayName((String)meaningMap.get("name"));
                }
                
                // Strip out any dangerous HTML, since we don't know where this came from
                @SuppressWarnings("unchecked")
                Map<String, String> mmap = (Map<String, String>)meaningMap.get("meanings");
                Iterator<Entry<String, String>> it = mmap.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<String, String> pairs = it.next();
                    pairs.setValue(WebUtil.safeHTML(pairs.getValue()));
                }
                
                newType.setMeanings(mmap);
                
                Iterator<ESignatureType> st = signatureTypes.iterator();
                boolean unique = true;
                ESignatureType tmpType;
                while(st.hasNext()) {
                    tmpType = st.next();
                    if(tmpType.getName().equalsIgnoreCase(newType.getName())) {
                        unique = false;
                        break;
                    }
                }
                if(unique) {
                    signatureTypes.add(newType);
                    
                    getContext().saveObject(getConfiguration());
                    getContext().commitTransaction();
    
                    result.setStatus(RequestResult.STATUS_SUCCESS);
                }
                else {
                    ArrayList<String> errors = new ArrayList<String>(1);
                    errors.add((new Message(MessageKeys.ESIG_DUPLICATE_ERROR).getLocalizedMessage()).replaceAll("\\{0\\}", newType.getName()).replaceAll("\\{1\\}", newType.getDisplayName()));
                    result.setErrors(errors);
                }
            }
            catch (GeneralException ex) {
                if (log.isErrorEnabled()) {
                    log.error(ex.getMessage(), ex);
                }
                ArrayList<String> errors = new ArrayList<String>(1);
                errors.add(ex.getLocalizedMessage());
                result.setErrors(errors);
            }
        }
        
        return result;
    }

    /**
     * Deletes the specified meaning.
     *
     * @param name The meaning name.
     * @return The result.
     * @throws GeneralException
     */
    @DELETE
    @Path("meanings/{name}")
    public RequestResult deleteMeaning(@PathParam("name") String name) throws GeneralException
    {
        // this is only used in the system setup right now so auth as system administrator
        authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));

        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        name = decodeRestUriComponent(name);

        List<ESignatureType> signatureTypes = getElectronicSignatureTypes();
        if (null != signatureTypes) {
            Iterator<ESignatureType> itr = signatureTypes.iterator();
            while (itr.hasNext()) {
                ESignatureType signatureType = itr.next();
                if (name.equals(signatureType.getName())) {
                    try {
                        itr.remove();

                        getContext().saveObject(getConfiguration());
                        getContext().commitTransaction();

                        result.setStatus(RequestResult.STATUS_SUCCESS);
                    } catch (GeneralException ex) {
                        if (log.isErrorEnabled()) {
                            log.error(ex.getMessage(), ex);
                        }
                    }

                    break;
                }
            }
        }

        return result;
    }
    
    @POST
    @Path("auth")
    public RequestResult checkAuth(@FormParam("accountId") String accountId, @FormParam("password") String password, @FormParam("objectType") String type, @FormParam("objId") String objId)
            throws GeneralException {
        
        
        if(type.equals(SignatureObjectType.CERTICATION_TYPE.toString())) {
     	    Certification cert = getContext().getObjectById(Certification.class, objId);
            if(cert != null) {
                //Don't throw exception if null. Client will handle this for now.
                authorize(new CertificationAuthorizer(cert));
            } else {
                log.warn(String.format("Certificaion with id %s not found when trying to electronically sign", objId));
            }
        } else if(type.equals(SignatureObjectType.WORKITEM_TYPE.toString())) {
        	WorkItem wi = getContext().getObjectById(WorkItem.class, objId);
        	if(wi != null) { 
        	    authorize(new WorkItemAuthorizer(wi));
        	} else {
        	    log.warn(String.format("WorkItem with id %s not found when trying to electronically sign", objId));
        	}
        } else {
        	throw new UnauthorizedAccessException(new Message(MessageKeys.UI_ESIG_INVALID_OBJECT_TYPE, type));
        }
        
        

        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);
        
        try {
            Identity ident = getNotary().authenticate(accountId, password); // throws AuthenticationFailureException

            // authorization here is tricky because it may not be the identity name
            // that was used to login. this approach gives the caller no special info
            // if credentials were correct but authentication failed or credentials were
            // just incorrect
            if (!isAuthorized(new IdentityMatchAuthorizer(ident))) {
                return result;
            }
            
            result.setStatus(RequestResult.STATUS_SUCCESS);
        }
        catch (GeneralException | ExpiredPasswordException e) {
            // Only show the errors if DETAILED_LOGIN_STYLE is true.
            if(reportDetailedLoginErrors()) {
                ArrayList<String> errors = new ArrayList<String>(1);
                errors.add(e.getLocalizedMessage());
                result.setErrors(errors);
            }
        }

        return result;
    }

    /**
     * Gets a list of the configured electronic signature types.
     *
     * @return The list of electronic signature types.
     * @throws GeneralException
     */
    private List<ESignatureType> getElectronicSignatureTypes() throws GeneralException
    {
        List<ESignatureType> signatureTypes = null;
        if (signatureTypesDefined()) {
            Object typesObj = getConfiguration().getAttributes().get(ESignatureType.CONFIG_ATT_TYPES);
            if (null == typesObj) {
                signatureTypes = new ArrayList<ESignatureType>();
                getConfiguration().getAttributes().put(ESignatureType.CONFIG_ATT_TYPES, signatureTypes);
            } else if (typesObj instanceof List) {
                signatureTypes = (List<ESignatureType>)typesObj;
            }
        }

        return signatureTypes;
    }

    /**
     * Determines if any signatures types have been defined to list.
     *
     * @return True if signature types appear to be defined, false otherwise.
     * @throws GeneralException
     */
    private boolean signatureTypesDefined() throws GeneralException
    {
        return null != getConfiguration() &&
               null != getConfiguration().getAttributes() &&
               getConfiguration().getAttributes().containsKey(ESignatureType.CONFIG_ATT_TYPES);
    }

    /**
     * Gets the electronic signature configuration.
     *
     * @return The configuration.
     * @throws GeneralException
     */
    private Configuration getConfiguration() throws GeneralException
    {
        if (null == _config) {
            _config =  getContext().getObjectByName(Configuration.class, Configuration.ELECTRONIC_SIGNATURE);
            
            // If it's null the config object was not imported, so create one.
            if(null == _config) {
                _config = new Configuration();
                _config.setName(Configuration.ELECTRONIC_SIGNATURE);
                Attributes<String, Object> attribs = new Attributes<String, Object>(1);
                attribs.put(ESignatureType.CONFIG_ATT_TYPES, new ArrayList<ESignatureType>());
                _config.setAttributes(attribs);
            }
        }

        return _config;
    }

}
