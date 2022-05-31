/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base class for SailPointObject DTOs providing common utility methods.
 * 
 * This has methods similar to PageCodeBase and BaseBean but we don't
 * extend either because they have assumptions about their lifespan
 * and cache things from the faces context.  DTOs can live on the
 * HttpSession over several requests so we can't cache anything.
 *
 * In retrospect we could have just fixed PageCodeBase to stop
 * caching, but I'm taking the opportunity to sift and find
 * what we really need.
 *
 * Author: Jeff
 * 
 */

package sailpoint.web;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Resolver;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.util.WebUtil;

@SuppressWarnings("serial")
@XMLClass
public class BaseDTO extends AbstractXmlObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(BaseDTO.class);

    /**
     * HttpSession attribute where the time zone is stored.
     */
    public static final String SESSION_TIMEZONE = 
    PageCodeBase.SESSION_TIMEZONE;

    /**
     * Unique generated id. 
     * Note that this is NOT the Hibernate id, it is always
     * generated so we have a reliable way to refer to DTO's
     * representing new objects.
     */
    String _uid;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public BaseDTO() {
        _uid = WebUtil.buildValidComponentId(uuid());
    }

    @XMLProperty
    public String getUid() {
        return _uid;
    }

    /**
     * This should only be set if you're cloning a DTO and
     * want to preserve the same uniqe id.
     */
    public void setUid(String s) {
        _uid = s;
    }
    
    /**
     * Pseudo property that must be overloaded by the subclass if
     * the ParentDTO wants to use methods like getChildSummary that
     * want to display and identify children using a readable name.
     *
     * Have to be careful not to conflict with all the other "name"
     * and "displayName" properties the DTO subclass is likely to have.
     */
    public String getUIName() {
        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    static public String uuid() {
        return Util.uuid();
    }

    /**
     * Collapse empty strings to null since we often get
     * them from JSF components.
     */
    public String trim(String src) {
        if (src != null) {
            src = src.trim();
            if (src.length() == 0)
                src = null;
        }
        return src;
    }

    /**
     * Trim to a fixed size.
     */
    public String trim(String src, int max) {
        String suffix = "...";
        if (src != null) {
            src = src.trim();
            if (src.length() > max) {
                src = src.substring(0, max);
                if (src.length() == 0)
                    src = null;
                else
                    src += suffix;
            }
        }            
        return src;
    }

    /**
     * Clone a DTO using XML.
     */
    @SuppressWarnings("unchecked")
	public <T extends BaseDTO> T xmlclone(T src) {
        T neu = null;
        if (src != null) {
            // AbstractXmlObject does the work, we just downcast
            // note that we have no XMLResolver here because DTO's
            // are not allowed to contain object references, this might
            // be bad if we allow Attributes maps to contain references
            try {
                neu = (T)src.deepCopy(null);
            }
            catch (GeneralException e) {
                // should never happen here since we don't resolve references
                log.error(e);
            }

            // the uid will have been regenerated, must copy it
            // hmm, no the XML attribute should have overwritten 
            // the default value.
            neu.setUid(src.getUid());

        }
        return neu;
    }

    /**
     * Clone a SailPointObject using XML.
     */
    @SuppressWarnings("unchecked")
	public <T extends SailPointObject> T spoclone(T src) 
        throws GeneralException {
        T neu = null;
        if (src != null) {
            neu = (T)src.deepCopy((Resolver)getContext());
        }
        return neu;
    }

    /**
     * Compare two DTOs by uid.
     */
    public boolean isClone(BaseDTO other) {

        // we should never have a null uid
        return (_uid != null && _uid.equals(other.getUid()));
    }

    /**
     * Search for a BaseDTO in a list by uid.
     */
    public <T extends BaseDTO> BaseDTO find(List<T> list, String uid) {
        BaseDTO found = null;
        if (list != null && uid != null) {
            for (BaseDTO dto : list) {
                if (uid.equals(dto.getUid())) {
                    found = dto;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Replace one BaseDTO in a list with another or add it
     * to the end of the list.  A common operation when
     * editing clones of child objects.
     */
    public <T extends BaseDTO> void replace(List<T> list, T neu) {
        if (list != null) {
            BaseDTO old = null;
            for (int i = 0 ; i < list.size() ; i++) {
                BaseDTO dto = list.get(i);
                if (dto.isClone(neu)) {
                    old = dto;
                    list.set(i, neu);
                    break;
                }
            }

            if (old == null)
                list.add(neu);
        }
    }

    /**
     * Remove one BaseDTO in a list identified by id.
     */
    public <T extends BaseDTO> void remove(List<T> list, String id) {
        if (list != null) {
            @SuppressWarnings("unchecked")
			T dto = (T) find(list, id);
            if (dto != null)
                list.remove(dto);
        }
    }

    /**
     * Resurect a SailPointObject from an id or name we've been managing.
     */
    public <T extends SailPointObject> T resolveById(Class<T>cls, String id) {

        T obj = null;
        SailPointContext con = getContext();

        if (id != null) {
            try {
                obj = con.getObjectById(cls, id);

                if (obj == null) {
                    // deleted out from under us, a problem?
                    // just leave the last scope
                    log.warn("Lost reference: " + cls.getSimpleName() + ":" +
                            id);
                }
            }
            catch (Throwable t) {
                // is this worth propagating?
                log.error(t.getMessage());
            }

        }
        return obj;
    }

    /**
     * Resurect a SailPointObject from a name we've been managing.
     */
    public <T extends SailPointObject> T resolveByName(Class<T>cls, String name) {

        T obj = null;
        SailPointContext con = getContext();

        if (name != null) {
            try {
                obj = con.getObjectByName(cls, name);

                if (obj == null) {
                    // deleted out from under us, a problem?
                    // just leave the last scope
                    log.warn("Lost reference: " + cls.getSimpleName() + ":" +
                            name);
                }
            }
            catch (Throwable t) {
                // is this worth propagating?
                log.error(t.getMessage());
            }

        }
        return obj;
    }


    /**
     * Refresh an object reference.  The given object may have been left
     * behind from a previous request and is no longer in the 
     * Hibernate session. The returned object will be in the current session.
     * The return may be null if the object was deleted.
     */
    @SuppressWarnings("unchecked")
	public <T extends SailPointObject> T refresh(T src) throws GeneralException {
        T fresh = null;
        if (src != null && src.getId() != null) {
            fresh =  (T) getContext().getObjectById(getClass(src), src.getId());
        }
        return fresh;
    }

    /**
     * Refresh a list of object references.
     */
    public <T extends SailPointObject> void refresh(List<T> objects) throws GeneralException {
        if (objects != null) {
            for (int i = 0 ; i < objects.size() ; i++) {
                T o = objects.get(i);
                objects.set(i, refresh(o));
            }
        }
    }

    /**
     * Extremely rare instanceof Hibernate leaking into the UI layer
     * so we don't have to put another method in SailPointContext
     * or make the UI dependent HibernatePersistenceManager.
     *
     * This has to be used whenever you want to get a Class to
     * pass into a SailPointContext method using the getClass
     * method of a SailPointObject instance.  For example:
     *
     *    con.getObject(something.getClass(), ...
     *
     * Because Hibernate uses CGLib to wrap proxy classes areound
     * lazy loaded objects what you get back from Object.getClass is
     * not necessarily a "real" class and Hibernate isn't able to
     * deal with them.  Hibernate thoughtfully provides a utility
     * to unwrap the proxy class that they're apparently too lazy
     * to use themselves.
     */
    @SuppressWarnings({"rawtypes"})
	public Class getClass(SailPointObject src) {

        return org.hibernate.Hibernate.getClass(src);
    }

    /**
     * Return the name of an object with an identifier posted from 
     * a form.  Until we can reach agreement on how objects should
     * be identified this is necessary when using suggest components
     * which usually post ids.  The newer DTO's need to represent
     * references as names so they can be directly displayed.
     */
    public <T extends SailPointObject> String getName(Class<T> cls, String id) {

        String name = id;
        if (id != null && isDatabaseId(id)) {
            try {
                SailPointObject obj = getContext().getObjectById(cls, id);
                if (obj != null)
                    name = obj.getName();
            }
            catch (GeneralException e) {
                // leave it asis
            }
        }
        return name;
    }

    /**
     * Return the database id of an object referneced by name.
     * Used in a few cases where we store object names in an 
     * attributes map, but while they are being edited in the UI
     * we want to use infrastructure like the UtilBean methods
     * that want to deal with things as ids.  After editing,
     * you would use getName above to convert the id back into
     * a name for storage.  
     */
    public <T extends SailPointObject> String getId(Class<T> cls, String name) {

        String id = name;
        if (name != null && !isDatabaseId(name)) {
            try {
                SailPointObject obj = getContext().getObjectByName(cls, name);
                if (obj != null)
                    id = obj.getId();
            }
            catch (GeneralException e) {
                // leave it asis
            }
        }
        return id;
    }

    /**
     * Utility to see if something smells like a Hibernate id.
     * Saves having to do a database hit in getName if we're
     * using a component that posts names.  This could be useful
     * in several places but we can't be 100% sure this isn't a name.
     * Changing id generation algorithms or Hibernate releases
     * may also invalidate this!
     * 
     * Hibernate ids look like this: 2c9081cf1adf88d8011adf8cd0dc019d
     */
    public boolean isDatabaseId(String id) {
        boolean isId = false;
        if (id != null && id.length() == 32) {
            isId = true;
            for (int i = 0 ; i < id.length() ; i++) {
                char ch = id.charAt(i);
                if (Character.digit(ch, 16) < 0) {
                    isId = false;
                    break;
                }
            }
        }
        return isId;
    }

    /**
     * Clean out a Map of values that can be defaulted to reduce
     * clutter in the XML.
     */
    public <T1, T2> void cleanMap(Map<T1, T2> map) {
        if (map != null) {
            Iterator<Map.Entry<T1, T2>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<T1, T2> ent = it.next();
                Object value = ent.getValue();
                boolean remove = false;
                if (value instanceof Integer)
                    remove = (((Integer)value).intValue() == 0);
                else if (value instanceof Boolean)
                    remove = (((Boolean)value).booleanValue() == false);
                else if (value instanceof String)
                    remove = (((String)value).length() == 0);
                if (remove)
                    it.remove();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Server Context
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the current IdentityIQ context.
     */
    @JsonIgnore
    public SailPointContext getContext() {
        SailPointContext con = null;
        try {
            con = SailPointFactory.getCurrentContext();
        }
        catch (Exception e) {
            log.error(e);
        }
        return con;
    }

    /**
     * Get the current faces context.
     */
    @JsonIgnore
    public FacesContext getFacesContext() {

        return FacesContext.getCurrentInstance();
    }

    /**
     * Get a map of all request parameters.
     */
    @JsonIgnore
    public Map<String, String> getRequestParameters() {
        Map<String, String> requestMap = null;
        FacesContext fc = getFacesContext();
        if (null != fc) {
            ExternalContext external = fc.getExternalContext();
    		requestMap = external.getRequestParameterMap();
        }
        return requestMap;
	}

    /**
     * Get one request parameter.
     */
	public String getRequestParameter(String name) {
		Map<String, String> map = getRequestParameters();
		return (String) map.get(name);
	}

    /**
     * Get a Map representing the HttpSession attributes.
     */
    @JsonIgnore
    public Map<String, Object> getSessionScope() {
        Map<String, Object> sessionMap = null;
        FacesContext fc = getFacesContext();
        if (null != fc) {
            ExternalContext external = fc.getExternalContext();
            sessionMap = external.getSessionMap();
        }
        return sessionMap;
    }

    /**
     * Get the current Locale.
     */
    @JsonIgnore
    public Locale getLocale() {
        Locale locale = null;
        FacesContext fc = getFacesContext();
        if (null != fc) {
            locale = fc.getViewRoot().getLocale();
        }
		return locale;
	}
    
    /**
     * Get the current user time zone.
     */
    @JsonIgnore
    public TimeZone getUserTimeZone() {
        Map<String, Object> sessionMap = getSessionScope();
        return ((sessionMap != null) ? (TimeZone) sessionMap.get(SESSION_TIMEZONE) : TimeZone.getDefault());
    }

    /**
     * Translate a message according to the current local and time zone.
     */
    public String getMessage(Message msg) {
        String text = null;
        if (msg != null)
            text = msg.getLocalizedMessage(getLocale(), getUserTimeZone());
        return text;
    }

    /**
     * Translate a message key and arguments.
     */
    public String getMessage(String key, Object... args) {

        return getMessage(new Message(key, args));
	}

    /**
     * Create a FacesMessage with summary and detail text taken
     * from two localized message objects.  The severity is derived
     * fom the type of the summary first, then the detail.
     *
     * This was adapted from BaseBean so BaseDTOs can add messages
     * to the FacesContext just like BaseBean.
     */
    public FacesMessage getFacesMessage(Message summary,
                                        Message detail) {

        FacesMessage msg = null;
        if (summary != null || detail != null) {

            Message.Type type =
                (summary != null) ? summary.getType() : detail.getType();

            FacesMessage.Severity severity = null;
            switch (type) {
                case Error: severity = FacesMessage.SEVERITY_ERROR; break;
                case Warn:  severity = FacesMessage.SEVERITY_WARN; break;
                default:    severity = FacesMessage.SEVERITY_INFO; break;
            }

            String summaryText = getMessage(summary);
            String detailText = getMessage(detail);

            msg = new FacesMessage(severity, summaryText, detailText);
        }

        return msg;
    }

    /**
     * Add a message to the page.
     */
    public void addMessage(Message summary,
                           Message detail) {

        FacesContext fc = getFacesContext();
        fc.addMessage(null, getFacesMessage(summary, detail));
    }

    public void addMessage(Message msg) {
        addMessage(msg, null);
    }

    public void addMessage(GeneralException e) {
        addMessage(e.getMessageInstance());
    }

    public void addMessage(Throwable t) {
        // punt and display the raw exception text
        addMessage(new Message(t.toString()));
    }

    /**
     * Should only be used for "not supposed to happen" errors.
     */
    public void addMessage(String msg) {
        addMessage(new Message(msg), null);
    }
    
    /**
     * Return whether the current request has any errors (ie - any messages at
     * error level or above).
     * 
     * @return Return true if the current request has errors, false otherwise.
     */
    public boolean getHasErrors() {
        // May want to also check the session messages.
        FacesMessage.Severity sev = getFacesContext().getMaximumSeverity();
        return (null != sev) && ((sev.compareTo(FacesMessage.SEVERITY_ERROR)) >= 0);
    }
}
