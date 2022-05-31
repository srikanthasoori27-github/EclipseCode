/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * Various utilities for analyzing relationships between SailPointObjects.
 * Objects usually can't do this themselves because they don't have
 * access to a SailPointContext, nor is the interface allowed
 * in the sailpoint.object package.
 *
 * Meter range: 70-79
 *
 * Author: Jeff
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;

import sailpoint.api.PersistenceManager.LockParameters;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeDefinition.UserInterfaceInputType;
import sailpoint.object.AttributeMetaData;
import sailpoint.object.Attributes;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationLink;
import sailpoint.object.ClassLists;
import sailpoint.object.Configuration;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.DirectAssignment;
import sailpoint.object.EmailTemplate;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Identity.WorkgroupNotificationOption;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.LinkExternalAttribute;
import sailpoint.object.LockInfo;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.Question;
import sailpoint.object.Reference;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.Target;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.persistence.HibernatePersistenceManager;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.messages.MessageKeys;

/**
 * Utility class providing methods to load and analyze
 * <code>SailPointObject</code> subclasses.
 */
public class ObjectUtil {

    private static final Log log = LogFactory.getLog(ObjectUtil.class);


    /**
     * The minimum length of any encoded string.
     */
    public static final int MINIMUM_ENCODED_LENGTH = 26;

    public static final int MAX_IN_QUERY_SIZE = 1000;
    /**
     * The delimiter character between the key alias and encoded ciphertext
     */
    public static final char ENCODING_DELIMITER = ':';

    // delimiter between compound attribute values, e.g. Question:1:password
    public static final String DEFAULT_NAME_DELIMITER = ":";


    //////////////////////////////////////////////////////////////////////
    //
    // General
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if the given string is a unique Hibernate identifier
     * rather than a user specified name.
     *
     * @ignore
     * Extremely complex patent-pending heuristics are employed to
     * to determine conclusively if that dude looks like a lady.
     */
    public static boolean isUniqueId(String id) {
        boolean yesItIs = false;
        if (id != null &&
            // probably JVM specific?
            id.length() == 32) {

            // must contain only hex digits, we filter out - from VMIDs
            yesItIs = true;
            for (int i = 0 ; i < id.length() ; i++) {
                char ch = id.charAt(i);
                
                if (!isValidHexDigit(ch)) {
                    yesItIs = false;
                    break;
                }
            }
        }
        return yesItIs;
    }

    /**
     * Extracts the last part of a compound attribute name using the default delimiter.
     * @param compoundName - attribute name from which to extract the last part.
     * @return - the last part of the compound name or the name if no delimiters found. If input is null/empty,
     * null/empty will be returned.
     */
    public static String extractName(String compoundName) {
        return extractName(compoundName, DEFAULT_NAME_DELIMITER);
    }

    /**
     * Extracts the last part of a compound attribute name, e.g. Question:0:password.
     *
     * @param compoundName - attribute name from which to extract the last part.
     * @param delimiter - separator used in the compound  name. If no delimiter provided, the default will be used.
     * @return - the last part of the compound name or the name if no delimiters found. If input is null/empty,
     * null/empty will be returned.
     */
    public static String extractName(String compoundName, String delimiter) {
        delimiter = StringUtils.isEmpty(delimiter) ? DEFAULT_NAME_DELIMITER : delimiter;

        return (StringUtils.isNotEmpty(compoundName) && compoundName.contains(delimiter) ?
                (compoundName.substring(compoundName.lastIndexOf(delimiter) + 1)) : compoundName);
    }

    /**
     * Remove any secret values from the items in the given ApprovalSet. This will handle names with
     * simple formats (password) or compound names (myapp:mysys:password) where compound elements are
     * separated by colon. Note, this method modifies the ApprovalSet.
     *
     * @param approvalSet  The ApprovalSet to scrub.
     */
    public static void scrubPasswords(ApprovalSet approvalSet) {
        if (approvalSet != null && approvalSet.getItems() != null) {
            for (ApprovalItem approvalItem : approvalSet.getItems()) {
                ObjectUtil.scrubPasswords(approvalItem.getAttributes());
                String itemName = approvalItem.getName();

                // check to make sure the name is not a compound value separated by colon
                if (isSecret(extractName(itemName))) {
                    approvalItem.setValue(null);
                }
            }
        }
    }

    /**
     * Scrub the passwords item values from the approval set and return clone. Original is not modified.
     *
     * @param approvalSet immutable approval set
     * @return approvalSet cloned ApprovalSet that has been scrubbed of password values
     */
    public static ApprovalSet scrubPasswordsAndClone(final ApprovalSet approvalSet) {
        // clone approval set
        ApprovalSet approvalSetCopy = new ApprovalSet(approvalSet);
        List<ApprovalItem> items = approvalSetCopy.getItems();
        for (ApprovalItem approvalItem : Util.safeIterable(items)) {
            if (isSecret(approvalItem.getName())) {
                approvalItem.setValue(null);
            }
        }

        return approvalSetCopy;
    }

    /**
     * Remove any secret values from the attributes and ApprovalSet in the given
     * WorkItem.  Note that this modifies the WorkItem.
     *
     * @param workitem  The WorkItem to scrub.
     */
    public static void scrubPasswords(WorkItem workitem) {
        if (workitem != null) {
            // look in the ApprovalSet ApprovalItems'attribute list.
            scrubPasswords(workitem.getApprovalSet());
            // Also look at the workitem's attributes
            Attributes<String, Object> attrs = workitem.getAttributes();
            scrubPasswords(attrs);
        }
    }
    
    /**
     * Remove any secret values from the given ProvisioningProject. Note that
     * this modifies the project.
     *
     * @param project  The ProvisioningProject to scrub.
     */
   public static void scrubPasswords(ProvisioningProject project) {
        if ( project != null ) {
            // scrub the master plan, too
            ProvisioningPlan master = project.getMasterPlan();
            
            List<ProvisioningPlan> plans = new ArrayList<ProvisioningPlan>();
            if (master != null) {
                plans.add(master);
            }
            
            List<ProvisioningPlan> projectPlans = project.getPlans();
            if (projectPlans != null) {
                plans.addAll(projectPlans);
            }

            for ( ProvisioningPlan plan : plans ) {
                scrubPasswords(plan);
            }
            // Bug 12406 - clear text passwords in used up IdentityRequests
            // TODO: Validate that in cloning, we cloned all the way down
            //       to cloning ExpansionItems and Questions
            List<ExpansionItem> items = project.getExpansionItems();
            if (items != null) {
                for (ExpansionItem item : items) {
                    //String appName = item.getApplication();
                    String attrName = item.getName();
                    if (ObjectUtil.isSecret(attrName) || project.isExpansionSecret(item)) {
                        item.setValue(null);
                    }
                }
            }
            // And the form questions
            if (project.getQuestionHistory() != null) {
                for (Question question : project.getQuestionHistory()) {
                    Field field = question.getField();
                    if (field != null && Field.TYPE_SECRET.equals(field.getType())) {
                        // secret field is secret
                        field.setValue(null);
                    }
                }
            }
        }
    }
    
   /**
    * Remove any secret values from the given Attributes. Note that this
    * modifies the attributes.
    *
    * @param attrs  The Attributes to scrub.
    */
    public static void scrubPasswords(Attributes<String, Object> attrs) {
        if (attrs != null) {

            // WI Archives can hold onto secret data.  So try and hide that data.
            // Step 1: go through the keys and find any that are for a password, like
            // "RealADWithDemoData:password" and "password"
            for (String key : attrs.getKeys()) {
                Object attrValue = attrs.get(key);

                if (attrValue instanceof String && ObjectUtil.isSecret(extractName(key))) {
                    // attribute name is secret, scrub it
                    attrs.put(key, null);
                }
                else if (attrValue instanceof ApprovalSet) {
                    scrubPasswords((ApprovalSet) attrValue);
                }
            }
        }
    }

    /**
     * Remove the value of the AttributeRequest if the name is secret, and also scrub passwords
     * from its arguments map
     * @param attributeRequest the AttributeRequest to scrub
     */
    public static void scrubPasswords(AttributeRequest attributeRequest) {
        if ( attributeRequest != null ) {
            if (attributeRequest.isSecret()) {
                attributeRequest.setValue(null);
                //bug#22106, arguments may contain currentPassword in raw text
                Attributes<String, Object> attrs = attributeRequest.getArguments();
                scrubPasswords(attrs);
            }
        }
    }

    /**
     * Removes any passwords contained in a Provisioning Plan.
     * 
     * @param plan The ProvisioningPlan to scrub
     */
    public static void scrubPasswords(ProvisioningPlan plan) {
        List<AccountRequest> acctReqs = plan.getAccountRequests();
        if ( acctReqs != null ) {
            for ( AccountRequest acctReq : acctReqs ) {
                List<AttributeRequest> reqs = acctReq.getAttributeRequests();
                if ( reqs != null  ) {
                    for ( AttributeRequest att : reqs ) {
                        scrubPasswords(att);
                    }
                }
            }
        }
    }

    /***
     * Return whether the given character is a valid hex digit.
     */
    public static boolean isValidHexDigit(final char ch) {
        
        if (Character.isDigit(ch)) {
            return true;
        }
        
        int lowerCaseInt = (int) Character.toLowerCase(ch);
        int lowerLimit = (int)'a';
        int upperLimit = (int)'f';
        
        return lowerCaseInt >= lowerLimit && lowerCaseInt <= upperLimit;
    }
    
    /**
     * Return true if the given string matches either the name or id
     * of the SailPointObject. This is used in a few places where
     * identity comparison needs to be made with objects that might
     * have been imported from test files, and these objects
     * have "soft" references to some other object by name rather
     * than id.
     */
    public static boolean isIdentifiedBy(SailPointObject obj, String id) {

        return (obj != null && id != null &&
                (id.equals(obj.getId()) || id.equals(obj.getName())));
    }
    
    
    
    /**
     * Save a given object, decache, and reattach the object
     */
    public static <T extends SailPointObject> void saveDecacheAttach(SailPointContext ctx, T object) 
            throws GeneralException {
        ctx.saveObject(object);
        ctx.commitTransaction();
        ctx.decache();
        ctx.attach(object);
    }

    /**
     * Delete all the objects that match a search filter.
     * Should only be used for modest sets, objects are brought in
     * one at a time to avoid overloading the object cache.
     */
    public static <T extends SailPointObject> int removeObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 QueryOptions ops)
        throws GeneralException {

        int count = 0;
        List<String> props = new ArrayList<String>();
        props.add("id");
        if (ops != null) {
            ops.setCloneResults(true);
        } else {
            ops = new QueryOptions();
            ops.setCloneResults(true);
        }
        Iterator<Object[]> it = context.search(cls, ops, props);
        while (it.hasNext()) {
            String id = (String)(it.next()[0]);
            SailPointObject o = context.getObjectById(cls, id);
            if (o != null) {
                context.removeObject(o);
                context.commitTransaction();
                context.decache(o);
                count++;
            }
        }
        
        return count;
    }

    /**
     * Load the Identity that is considered the owner of the context.
     */
    public static Identity getContextIdentity(SailPointContext context)
        throws GeneralException {

        Identity identity = null;
        String name = context.getUserName();
        if (name != null)
            identity = context.getObjectByName(Identity.class, name);

        return identity;
    }

    /**
     * Check to see if an object about to be saved has a name
     * that is already being used by a different object. This is
     * expected to be called by the UI prior to saving changes.
     * If you do not you will get an obscure Hibernate
     * "constraint violation" error with no clue as to what
     * constraint was violated (but it is usually the name since
     * there are not many not-null constraints).
     *
     * @ignore
     * We used to do this down in HibernatePersistenceManager but
     * it adds overhead when you know the object name hasn't changed
     * which is the case for all internal object processors like
     * the Aggregator and Identitizer.
     */
    public static void checkIllegalRename(SailPointContext context,
                                          SailPointObject obj)
        throws GeneralException {

        if (isIllegalRename(context, obj)){
            throw new GeneralException("An object with name '" +
                                                   obj.getName() +
                                                   "' already exists.");
        }
    }

    /**
     * Returns true of the given object has been renamed
     * that is already being used.
     *
     * This differs from checkIllegalRename in that it does not
     * throw if it finds a name collision.
     */
    public static boolean isIllegalRename(SailPointContext context,
                                          SailPointObject obj) throws GeneralException{
        String id = obj.getId();
        if (obj.isNameUnique() && id != null) {
            String name = obj.getName();
            if (name != null) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("name", obj.getName()));
                List<String> props = new ArrayList<String>();
                props.add("id");
                Class<SailPointObject> cls = getTheRealClass(obj);
                Iterator<Object[]> result = context.search(cls, ops, props);
                if (result != null && result.hasNext()) {
                    String other = (String)(result.next()[0]);
                    if (other != null && !other.equals(id))
                        return true;
                }
            }
        }

        return false;
    }

    /**
     * Given the generated name for an objct, this method ensures that the name does
     * not already exist. If it does, the count value is appended to the message. The method will continue
     * to increment count until it finds a unique name.
     *
     * @param ctx Sailpoint context
     * @param id The ID of the object
     * @param name The current name
     * @param clazz Class name
     * @param count Current number to be appended if the name is a duplicate. 0 is never appended.
     * @return A unique name for the object.
     */
     public static String generateUniqueName(SailPointContext ctx, String id, String name, Class clazz,
                                       int count) throws GeneralException{

        if (count > 0)
            name = new Message(MessageKeys.TASK_SCHED_DUP_NAME, name, count).getLocalizedMessage();

        QueryOptions ops = new QueryOptions(Filter.eq("name", name));
        Iterator<Object[]> it = ctx.search(clazz, ops, Arrays.asList("id"));

        if(it.hasNext()) {
            String rowId = (String)it.next()[0];
            if (id != null && id.equals(rowId)) {
                // if this is the one we're editing, then just use that name
                return name;
            } else if (count == 0) {
                count = 2;
            } else {
                count++;
            }

            return generateUniqueName(ctx, id, name, clazz, count);
        }

        return name;
    }

    /**
     * @exclude
     * Have to add a Hibernate dependency here to get the "real"
     * class from the stupid CGLIB "enhanced" class, because
     * Hibernate is unable to do it itself.  If you don't do this
     * passing the Object class down through the search() methods
     * will get errors.
     *
     * Ideally there should be a PersistenceManager method for this,
     * or a set of search interfaces that take an Object rather than
     * a Class.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Object> Class<T> getTheRealClass(T obj) {

        return org.hibernate.Hibernate.getClass(obj);
    }

    /**
     * Throw a GeneralException if the given object has a name that would cause
     * an illegal rename exception.
     *
     * @see #isIllegalRename(SailPointContext, SailPointObject)
     *
     * @ignore
     * jsl - why do we need this and checkIllegalRename?
     * just have one that does both.
     */
    public static void checkIllegalRenameOrNewName(SailPointContext context, SailPointObject obj)
        throws GeneralException {

        // only works if the object already has an id
        String id = obj.getId();
        if (obj.isNameUnique()) {
            final String name = obj.getName();

            QueryOptions queryByName = new QueryOptions();
            queryByName.add(Filter.eq("name", name));

            if (id == null) {
                if (name != null) {
                    List<String> props = new ArrayList<String>();
                    props.add("name");
                    Class<SailPointObject> cls = getTheRealClass(obj);
                    Iterator<Object[]> result = context.search(cls, queryByName, props);
                    if (result != null && result.hasNext()) {
                        throw new GeneralException("An object with name '" + name + "' already exists.");
                    }
                }
            } else {
                if (name != null) {
                    List<String> props = new ArrayList<String>();
                    props.add("id");
                    Class<SailPointObject> cls = getTheRealClass(obj);
                    Iterator<Object[]> result = context.search(cls, queryByName, props);
                    if (result != null && result.hasNext()) {
                        String other = (String)(result.next()[0]);
                        if (other != null && !other.equals(id))
                            throw new GeneralException("An object with name '" +
                                                       name +
                                                       "' already exists.");
                    }
                }
            }
        }
    }

    /**
     * Convert a list of objects to a list of names.
     */
    public static <T extends SailPointObject> List<String> getObjectNames(List<T> objects) {
        List<String> names = null;
        if (objects != null) {
            names = new ArrayList<String>();
            for (SailPointObject obj : objects)
                names.add(obj.getName());
        }
        return names;
    }
    
    /**
     * @see ObjectUtil#getObjectNames(List)     
     */
    public static <T extends SailPointObject> List<String> getObjectNames(Set<T> objects) {
    	List<T> convertedList = new ArrayList<T>();
    	convertedList.addAll(objects);
    	return getObjectNames(convertedList);
    }

    /**
     * Convert a list of objects to a list of ids.
     */
    public static <T extends SailPointObject> List<String> getObjectIds(List<T> objects) {
        List<String> ids = null;
        if (objects != null) {
            ids = new ArrayList<String>();
            for (SailPointObject obj : objects) {
                ids.add(obj.getId());
            }
        }
        return ids;
    }

    /**
     * Convenience method to get the list of object IDs for a given query.
     */
    public static List<String> getObjectIds(SailPointContext con, Class clazz, QueryOptions ops)
            throws GeneralException{
        Iterator<Object[]> iter = con.search(clazz, ops, Arrays.asList("id"));
        List<String> ids = null;
        while(iter != null && iter.hasNext()){
            if (ids == null)
                ids = new ArrayList<String>();
            ids.add((String)iter.next()[0]);
        }

        return ids;
    }

    /**
     * Take a detached object and "reswizzle" it so that any references
     * it might have to other autonomous objects will be represented
     * by objects in the given context (meaning the Hibernate cache).
     *
     * This is necessary to avoid Hibernate errors
     * such as "Found two representations of the same collection" when
     * trying to restore objects that have been deserialized from XML
     * THEN detached. This does not happen in many places, typically
     * saveObject is called and a commit is done soon after XML parsing and the
     * external references are still in the session.  But in a few
     * places like Workflower, RoleLifecycler, and TaskManager it is 
     * necessary to be able to save objects that were fetched or deXML'd and
     * the hibernate session has since gone into an unknown state,
     * often a full decache.
     *
     * @ignore
     * I'm still not positive why this happens, the Hibernate debug trace
     * isn't very helpful.  But we've seen this situation for Bundle
     * objects in the role modeler:
     *
     *    Bundle A --ineritance--> Bundle B --inheritance--> Bundle C
     *
     * When you first deserlaize Bundle A from XML within the workflow
     * it resolves Bundle B, and apparently through cascading you cannot
     * control Bundle C.  During the process of archiving fetch
     * the current verison of Bundle A, archive it, and do a full decache.
     * At this point there is a hierarchy of three Java objects. If
     * you now call context.saveobject() on Bundle A and commit you get
     * the "two representations".  This appears to have
     * something to do with Bundle B and/or Bundle C which are not
     * being reattached properly by saveOrUpdate. 
     * replicate() does not help either.
     *
     * Rather than pursue this into oblivion, in the few cases that
     * have this problem we'll rebuild the root object and replace
     * all references to detached external objects with objects that
     * have been freshly loaded into the cache.  This is done by
     * serializing the object back to XML and then deserializing,
     * letting the XMLResolver (the SailPointContext) fetch new
     * copies of all the external objects.
     *
     */
    public static SailPointObject recache(SailPointContext con, SailPointObject src)
        throws GeneralException {
        return recache(con, src, false);
    }

    /**
     * @see #recache(SailPointContext, SailPointObject)
     * @param con SailPointContext
     * @param src SailPointObject to recache
     * @param doAttach True if the object should be attached after the recache; false otherwise
     * @return A recached copy of the source object.
     * @throws GeneralException
     */
    public static SailPointObject recache(SailPointContext con, SailPointObject src, boolean doAttach)
        throws GeneralException {

        SailPointObject neu = src;
        if (src != null)
            neu = (SailPointObject)src.deepCopy((XMLReferenceResolver)con);
        if (doAttach) {
            con.attach(neu);
        }

        return neu;
    }

    /**
     * Return an object with the same id as the given object that is attached to
     * the session. This can be called after SailPointContext.decache() to get
     * objects attached back to the session. Note that this does not return the
     * same object, so if the calling code has any references to the object those
     * should be replaced with the object returned from this method.
     */
    public static <T extends SailPointObject> T reattach(SailPointContext ctx, T o)
        throws GeneralException {
        
        if (null != o) {
            Class<T> theRealClass = getTheRealClass(o);
            o = reattachWithPrejudice(ctx, theRealClass, o); 
        }
        
        return o;
    }

    /**
     * Return an object with the same id as the given object that is attached to
     * the session. This can be called after SailPointContext.decache() to get
     * objects attached back to the session. Note that this does not return the
     * same object, so if the calling code has any references to the object they
     * should be replaced with the object returned from this method. This is just
     * like the reattach() method except it bypasses the issues with CGLIB.
     * This is especially useful when reattaching an object that was previously 
     * attached to a stale session but whose class is known.
     */
    public static <T extends SailPointObject> T reattachWithPrejudice(SailPointContext ctx, Class <T> objClass, T o)
        throws GeneralException {

        if (null != o) {
            if (o.getId() != null) {
                o = ctx.getObjectById(objClass, o.getId());
            } else if (o.getName() != null) {
                o = ctx.getObjectByName(objClass, o.getName());
            } else {
                log.error("Attempt to reattach unidentified object");
            }
        }
        return o;
    }



    //////////////////////////////////////////////////////////////////////
    //
    // Origins
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Look in various places to find the identity that
     * is considered to be the "requester" of something.
     * This is used in several places: launchers of tasks,
     * owners of task results, launchers of workflow cases,
     * and requesters of work items.
     *
     * @ignore
     * We should try to represent this consistently with
     * a value for TaskSchedule.ARG_LAUNCHER in an arguments map.
     *
     * A few of the older api classes also support setting
     * the launcher directly in a Java field though this is rarely used.
     */

    public static Identity getOriginator(SailPointContext context,
                                         String launcher,
                                         Attributes<String,Object> arguments,
                                         TaskResult result)
        throws GeneralException {


        Identity originator = null;

        // explicit launchers set in the class win
        if (launcher != null)
            originator = context.getObjectByName(Identity.class, launcher);

        // then come task args, this is the preferred method
        if (originator == null && arguments != null) {
            launcher = arguments.getString(TaskSchedule.ARG_LAUNCHER);
            if (launcher != null)
                originator = context.getObjectByName(Identity.class, launcher);
        }

        // if we're inside a task, then TaskManager should have
        // set a launcher in the result, though it should have also
        // copied this to the task args so we would have found it above
        if (originator == null && result != null) {
            launcher = result.getLauncher();
            if (launcher != null)
                originator = context.getObjectByName(Identity.class, launcher);
        }

        // results can also have owners, though this would usually be the
        // same as launcher if we had one
        if (originator == null && result != null)
            originator = result.getOwner();

        // then is the context owner, for background tasks this
        // will usually be "Scheduler" and not resolve
        if (originator == null)
            originator = ObjectUtil.getContextIdentity(context);

        // fall back to admin
        // TODO: Might want a global config option for default originator
        // !! Would be much better if WorkItem requester didn't have
        // to be an Identity
        if (originator == null)
            originator = context.getObjectByName(Identity.class, BrandingServiceFactory.getService().getAdminUserName() );

        return originator;
    }

    /**
     * Return an originator (requester) that has the given name or ID.
     */
    public static Identity getOriginator(SailPointContext context,
                                         String launcher)
        throws GeneralException {

        return getOriginator(context, launcher, null, null);
    }

    /**
     * Return an originator (requester) that has the given name or ID,
     * or is the launcher in the given arguments map.
     */
    public static Identity getOriginator(SailPointContext context,
                                         String launcher,
                                         Attributes<String,Object> arguments)
        throws GeneralException {

        return getOriginator(context, launcher, arguments, null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Gatherers
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert an untyped list from the argument map into a list
     * of strings.
     *
     * @ignore
     *   Should be in the conversion tools?
     */
    @SuppressWarnings("unchecked")
    public static List<String> getStrings(List objects)
        throws GeneralException {

        List<String> strings = null;
        if (objects != null) {
            for (Object o : objects) {
                if (o != null) {
                    if (strings == null)
                        strings = new ArrayList<String>();
                    strings.add(o.toString());
                }
            }
        }
        return strings;
    }

    /**
     * @see #getObjects(SailPointContext, Class, Object, boolean, boolean, boolean, List)
     */
    public static <T extends SailPointObject> List<T> getObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 Object something,
                                                                 boolean trustRuleReturns,
                                                                 boolean throwExceptions)
        throws GeneralException {
        return getObjects(context, cls, something, trustRuleReturns, throwExceptions, true);
    }

    /**
     * @see #getObjects(SailPointContext, Class, Object, boolean, boolean, boolean, List)
     */
    public static <T extends SailPointObject> List<T> getObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 Object something,
                                                                 boolean trustRuleReturns,
                                                                 boolean throwExceptions, 
                                                                 boolean convertCSVToList)
        throws GeneralException {
        return getObjects(context, cls, something, trustRuleReturns, throwExceptions, convertCSVToList, null);
    }
    
    /**
     * @see #getObjects(SailPointContext, Class, Object, boolean, boolean, boolean, List)
     */
    public static <T extends SailPointObject> List<T> getObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 Object something,
                                                                 boolean trustRuleReturns,
                                                                 boolean throwExceptions,
                                                                 List<String> notFoundNames)
        throws GeneralException {
        return getObjects(context, cls, something, trustRuleReturns, throwExceptions, true, notFoundNames);
    }

    /**
     * Derive a list of objects from a value, typically a task
     * argument or a rule result. The value can be of these forms:
     *
     * <pre>
     *    - instance of the given class
     *    - name of an instance of the class
     *    - List of names of instances of the class
     *    - List of instances of the class
     * </pre>
     *
     * Throw if any names are unresolved.
     *
     * @ignore
     * We originally logged errors if the object would not resolve but since
     * this is sometimes used with "system" names like "Scheduler" or 
     * "LCM" we know that these won't resolve and don't want to put alarming
     * error messages in the log.  Softened to info logs for bug 9385
     *
     * jsl - this has become way too messy, we've got two very similar
     * implementations with one supporting convertCSVToList and
     * notFoundNames, and the other supporting References but only returning
     * a single value.  We should have ONE method implementation that takes a
     * Options object so we don't have to have a bazillion method signatures.
     */
    @SuppressWarnings("unchecked")
    public static <T extends SailPointObject> List<T> getObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 Object something,
                                                                 boolean trustRuleReturns,
                                                                 boolean throwExceptions,
                                                                 boolean convertCSVToList,
                                                                 List<String> notFoundNames)
        throws GeneralException {

        List<T> objects = null;

        Collection things = null;
        if (something instanceof Collection) {
            things = (Collection)something;
        }
        else if (cls.isInstance(something)) {
            things = new ArrayList();
            things.add(something);
        }
        else if (something instanceof Reference) {
            things = new ArrayList();
            things.add(something);
        }
        else if (something != null) {
            things = (convertCSVToList) ? Util.csvToList(something.toString(), true) : Arrays.asList(something);
        }

        if (things != null) {
            boolean breakIteration = false;
            for (Object o : things) {
                if (breakIteration) break;
                T obj = null;
                if (cls.isInstance(o)) {
                    if (trustRuleReturns)
                        obj = (T)o;
                    else {
                        // fetch it fresh
                        obj = context.getObjectById(cls, ((T)o).getId());
                    }
                }
                else if (o instanceof Reference) {
                    Reference ref = (Reference)o;
                    if (ref.getId() != null)
                        obj = context.getObjectById(cls, ref.getId());
                    else if (ref.getName() != null)
                        obj = context.getObjectByName(cls, ref.getName());
                    if (obj == null) {
                        if (throwExceptions)
                            throw new GeneralException("Unknown object: " + ref.getNameOrId());
                        else {
                            if (log.isInfoEnabled())
                                log.info("Unknown object: " + ref.getNameOrId());
                            
                            if (null != notFoundNames) {
                                notFoundNames.add(ref.getNameOrId());
                            }
                        }
                    }
                }
                else if (o != null) {
                    String name = o.toString();
                    //TODO: Is this always name?
                    obj = context.getObjectByName(cls, name);
                    if (obj == null) {
                        //Sigh, this object might just have a comma in the name
                        //let's give it another go
                        name = something.toString();
                        obj = context.getObjectByName(cls, name);
                        if (obj != null) {
                            breakIteration = true;
                        }
                    }
                    if (obj == null) {
                        if (throwExceptions)
                            throw new GeneralException("Unknown object: " + name);
                        else  {
                            if (log.isInfoEnabled())
                                log.info("Unknown object: " + name);
                            
                            if (null != notFoundNames) {
                                notFoundNames.add(name);
                            }
                        }
                    }
                }
                if (obj != null) {
                    if (objects == null)
                        objects = new ArrayList<T>();
                    objects.add(obj);
                }
            }
        }
        return objects;
    }

    /**
     * @see #getObjects(SailPointContext, Class, Object, boolean, boolean, boolean, List)
     */
    public static <T extends SailPointObject> List<T> getObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 Object something,
                                                                 boolean trustRuleReturns)
        throws GeneralException {

        return getObjects(context, cls, something, trustRuleReturns, true);
    }

    /**
     * @see #getObjects(SailPointContext, Class, Object, boolean, boolean, boolean, List)
     *
     * @ignore
     * This is the signature we've had for awhile.
     * Trust the object returned by the rule.
     */
    public static <T extends SailPointObject> List<T> getObjects(SailPointContext context,
                                                                 Class<T> cls,
                                                                 Object something)
        throws GeneralException {

        return getObjects(context, cls, something, true);
    }

    /**
     * Return a list of Identities known to be in the given
     * context (Hibernate cache). This is used when calling rules
     * that might return detached Identity objects.
     */
    public static List<Identity> getTrustedIdentities(SailPointContext context,
                                                      Object something)
        throws GeneralException {

        return getObjects(context, Identity.class, something, false);
    }

    /**
     * Get a single object using a variety of reference representations.
     * 
     * @ignore
     * jsl - this has become way too messy, we've got two very similar
     * implementations with one supporting convertCSVToList and
     * notFoundNames, and the other supporting References but only returning
     * a single value.  We should have ONE method implementation that takes a
     * Options object so we don't have to have a bazillion method signatures.
     * TODO: If String, need to determine if name or id
     */
    @SuppressWarnings("unchecked")
    public static <T extends SailPointObject> T getObject(SailPointContext context,
                                                          Class<T> cls,
                                                          Object something,
                                                          boolean trustRuleReturns,
                                                          boolean throwExceptions)
        throws GeneralException {

        T object = null;

        if (something instanceof Collection) {
            // hmm, I guess return the first element but you really
            // shouldn't be using this with a collection
            List<T> things = getObjects(context, cls, something,
                                        trustRuleReturns, throwExceptions);
            if (things != null && things.size() > 0)
                object = things.get(0);
        }
        else if (something instanceof Reference) {
            Reference ref = (Reference)something;
            if (ref.getId() != null)
                object = context.getObjectById(cls, ref.getId());
            else if (ref.getName() != null)
                object = context.getObjectByName(cls, ref.getName());
            if (object == null) {
                if (throwExceptions) {
                    throw new GeneralException("Unknown object: " + ref.getNameOrId());
                } else {
                    if (log.isInfoEnabled())
                        log.info("Unknown object: " + ref.getNameOrId());
                }
            }
        }
        else if (cls.isInstance(something)) {
            if (trustRuleReturns)
                object = (T)something;
            else {
                // fetch it fresh
                object = context.getObjectById(cls, ((T)something).getId());
            }
        }
        else if (something != null) {
            String name = something.toString();
            object = context.getObjectByName(cls, name);
            if (object == null) {
                if (throwExceptions) {
                    throw new GeneralException("Unknown object: " + name);
                } else {
                    if (log.isInfoEnabled())
                        log.info("Unknown object: " + name);
                }
            }
        }
        return object;
    }

    /**
     * @see #getObject(SailPointContext, Class, Object, boolean, boolean)
     */
    public static <T extends SailPointObject> T getObject(SailPointContext context,
                                                          Class<T> cls,
                                                          Object something)
        throws GeneralException {

        return getObject(context, cls, something, true, true);
    }

    /**
     * Sort a SailPointObject list by name.
     *
     * @ignore
     * This is used in a few cases where a stable processing order
     * is needed for the unit tests.  One example is the list of
     * IntegrationConfigs processed by RoleSynchronizer.
     */
    public static <T extends SailPointObject> void sortByName(List<T> objects) {
        Collections.sort(objects, new Comparator<T>() {
            public int compare(T o1, T o2) {
                String name1 = (null != o1) ? o1.getName() : null;
                String name2 = (null != o2) ? o2.getName() : null;
                return Util.nullSafeCompareTo(name1, name2);
            }
        });
    }
    
    /**
     * Returns a single object matching the given filter, regardless of how many matches 
     * the database found.  Return null if no matches are found
     * @param cls Class of the object being retrieved
     * @param filter Filter used to retrieve the object
     * @return A single object matching the given filter or null if no such object is found
     */
    public static <T extends SailPointObject> T getFirstObject(SailPointContext context, Class<T> cls, Filter filter) 
        throws GeneralException {
        QueryOptions options = new QueryOptions(filter);
        options.setResultLimit(1);
        T object = null;
        List<T> objects = context.getObjects(cls, options);
        if (!Util.isEmpty(objects)) {
            object = objects.get(0);
        }
        return object;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Retrieve the CertificationArchive for the certification with the given ID.
     *
     * @param  context  The context to use to load the object.
     * @param  certId   The ID of the certification for which the archive was
     *                  created.
     *
     * @return The CertificationArchive for the certification with the given ID,
     *         or null if there is not an archive for the given certification.
     */
    public static CertificationArchive getCertificationArchive(SailPointContext context,
                                                               String certId)
        throws GeneralException {

        if (null == certId)
            return null;

        CertificationArchive found = null;

        CertificationArchive ex = new CertificationArchive();
        ex.setCertificationId(certId);
        found = context.getUniqueObject(ex);

        // If we couldn't find an archive with this as the top-level
        // certification, search using the children ID list.
        if (null == found) {
            QueryOptions ops = new QueryOptions();
            List<String> certIds = new ArrayList<String>();
            certIds.add(certId);
            ops.add(Filter.containsAll("childCertificationIds", certIds));
            List<CertificationArchive> archives =
                context.getObjects(CertificationArchive.class, ops);
            if ((null != archives) && !archives.isEmpty()) {
                if (1 != archives.size()) {
                    throw new GeneralException("Found multiple archives containing " + certId);
                }
                found = archives.get(0);
            }
        }

        return found;
    }

    /**
     * Count the distinct values of the requested attribute on the given class
     * using the given QueryOptions.
     */
    public static int countDistinctAttributeValues(SailPointContext ctx,
                                                   Class<? extends SailPointObject> clazz,
                                                   QueryOptions qo,
                                                   String attrName)
        throws GeneralException {

        int count = -1;
        List<Ordering> orderBys = qo.getOrderings();
        try {
            // Having an ORDER BY is no bueno for SQL Server.  Make it vamoose
            // until we've run the count query.
            qo.setOrderings(new ArrayList<Ordering>());
            String prop = "count(distinct " + attrName + ")";
            Iterator<Object[]> results = ctx.search(clazz, qo, prop);
            count = ((Long) results.next()[0]).intValue();
        }
        finally {
            qo.setOrderings(orderBys);
        }

        return count;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CertificationLink
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Locate the referenced Certification, digging it out of an
     * archive if necessary. Note that this should be treated as a read-only
     * certification since it can be coming out of an archive. If you need
     * to have a read/write certification, use getCertificationArchive()
     * instead and save the modified Certification back in the archive with
     * CertificatioArchive.rezipCertification(Certification).
     * 
     * @deprecated  Most code should not be retrieving certifications from
     * archives. If archive retrieval is required, this should be done
     * explicitly using {@link #getCertificationArchive(SailPointContext, String)}.
     */
    @Deprecated
    public static Certification getCertification(SailPointContext context,
                                                 CertificationLink link)
        throws GeneralException {

        Certification cert = null;
        if (link != null) {
            String certid = link.getId();
            if (certid != null) {
                cert = context.getObjectById(Certification.class, certid);
                if (cert == null) {
                    CertificationArchive arch =
                        getCertificationArchive(context, certid);
                    if (arch != null)
                        cert = arch.decompress(context, certid);
                }
            }
        }

        return cert;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity/IdentitySnapshot
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get all the snapshots for an identity.
     * Use with care since there can be a lot.
     */
    public static List<IdentitySnapshot> getSnapshots(SailPointContext context,
                                                      Identity identity)
        throws GeneralException {

        List<IdentitySnapshot> snapshots = null;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identityName", identity.getName()));
        ops.setOrderBy("created");
        ops.setOrderAscending(false);

        snapshots = context.getObjects(IdentitySnapshot.class, ops);

        return snapshots;
    }

    /**
     * Return the id and creation date of the most recent
     * IdentitySnapshot.  Use a projection query since it
     * is expensive to load these.
     */
    public static Object[] getRecentSnapshotInfo(SailPointContext context,
                                                 Identity identity)
        throws GeneralException {

        Object[] result = null;

        QueryOptions ops = new QueryOptions();
        String [] propertyProjection = new String [] { "id", "created" };
        ops.setOrderBy("created");
        ops.setOrderAscending(false);
        ops.setResultLimit(1);
        ops.add(Filter.eq("identityName", identity.getName()));

        Iterator<Object []> sresult =
            context.search(IdentitySnapshot.class, ops, Arrays.asList(propertyProjection));

        if (sresult != null && sresult.hasNext()) {
            result = sresult.next();
        }

        return result;
    }


    /**
     * Locate the most recent IdenentitySnapshot for an Identity.
     *
     * @ignore
     * There are several ways this could be done, but we're assuming
     * that there can be a lot of these and they may be large, so avoid
     * bringing them into memory where possible.
     *
     * Not sure how to do MAX() through the Projection interface,
     * so just ask for ids and dates ordered by descending date
     * and pick the first one.
     *
     */
    public static IdentitySnapshot getRecentSnapshot(SailPointContext context,
                                                     Identity identity)
        throws GeneralException {

        IdentitySnapshot snap = null;
        Meter.enter(70, "Cert: getRecentSnapshotInfo");
        Object[] row = getRecentSnapshotInfo(context, identity);
        Meter.exit(70);
        if (row != null) {
            String id = (String)row[0];
            Meter.enter(71, "Cert: fetch recent snapshot");
            snap = context.getObjectById(IdentitySnapshot.class, id);
            Meter.exit(71);
        }

        return snap;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity/PolicyVioltion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get all the policy violations for an identity.
     *
     * @ignore
     * Originally we did store a reference directly from the Identity to
     * the PolicyViolations.  If we decide to do that, we don't really need
     * this function any more, but it will still work.
     */
    public static List<PolicyViolation> getPolicyViolations(SailPointContext context,
                                                            Identity identity)
        throws GeneralException {

        List<PolicyViolation> violations = null;

        // ignore transient objects, useful for some unit tests
        if (identity.getId() != null) {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity", identity));
            ops.add(Filter.eq("active", true));

            violations = context.getObjects(PolicyViolation.class, ops);
        }

        return violations;
    }

    /**
     * Return a Filter that will check if the given identity is the "owner" of
     * an object or is in a workgroup that owns the object.
     * 
     * @param  identity  The Identity to check for ownership.
     */
    public static Filter getOwnerFilterForIdentity(Identity identity)
        throws GeneralException {

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("owner", identity));

        if (identity.getWorkgroups() != null && !identity.getWorkgroups().isEmpty()) {
            filters.add(Filter.in("owner", identity.getWorkgroups()));
        }

        return Filter.or(filters);
    }

    /**
     * Return a Filter that will check if the given identity is the "owner" of
     * an object or is in a workgroup that owns the object.
     * This filter should be used when we want to perform the query against WorkItemArchive
     * @param  identity  The Identity to check for ownership.
     */
    public static Filter getOwnerNameArchiveFilterForIdentity(Identity identity)
        throws GeneralException {

        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("ownerName", identity.getName()));

        if (identity.getWorkgroups() != null && !identity.getWorkgroups().isEmpty()) {
            List <String> workGroupsNames = new ArrayList <String> ();
            for (Identity id : identity.getWorkgroups()){
                workGroupsNames.add(id.getName());
            }
            if (!workGroupsNames.isEmpty()) {
                filters.add(Filter.in("ownerName", workGroupsNames));
            }
        }

        return Filter.or(filters);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity/Link
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the display name or native identity of the account on the given
     * application on the given identity with the given nativeIdentity.
     *
     * @param  ctx             The SailPointContext to use.
     * @param  identityName    The name of the Identity.
     * @param  appName         The name of the application of the account.
     * @param  nativeIdentity  The nativeIdentity of the account.
     *
     * @return The display name or native identity of the account on the
     *         given application on the given identity with the given
     *         nativeIdentity.
     */
    public static String getAccountId(SailPointContext ctx, String identityName,
                                      String appName, String instance,
                                      String nativeIdentity)
        throws GeneralException {

        String accountId = null;

        Filter f;
        if (instance == null)
            f = Filter.and(Filter.eq("identity.name", identityName),
                           Filter.eq("application.name", appName),
                           Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));
        else
            f = Filter.and(Filter.eq("identity.name", identityName),
                           Filter.eq("application.name", appName),
                           Filter.eq("instance", instance),
                           Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));

        // jsl - since we're bothering with a search could just
        // return the two interesting fields in a projection and
        // avoid the fetch
        Link link = ctx.getUniqueObject(Link.class, f);
        if (null != link) {
            accountId = link.getDisplayName();
            accountId = (null != accountId) ? accountId : link.getNativeIdentity();
        }

        return accountId;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the EmailTemplate designated with the given key in system config.
     *
     * @param  ctx  The SailPointContext to use to load the objects.
     * @param  key  The key in the system configuration object that holds the
     *              name of the email template.
     *
     * @return The EmailTemplate designated with the given key in system config
     *         or null if the system config or email template cannot be loaded.
     */
    public static EmailTemplate getSysConfigEmailTemplate(SailPointContext ctx,
                                                          String key)
        throws GeneralException {

        EmailTemplate template = null;
        Configuration config = ctx.getConfiguration();
        if (null != config) {
            String templateName = Util.getString(config.getString(key));
            if (null != templateName) {
                template = ctx.getObjectByName(EmailTemplate.class, templateName);
            }
        }
        return template;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rules
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given the canonical name of a rule, look in the configuration
     * to locate the Rule object that implements it.
     */
    public static Rule lookupRule(SailPointContext context,
                                  String abstractName)
        throws GeneralException {

        Rule rule = null;
        Configuration config = context.getConfiguration();
        if (config != null) {
            String name = config.getString(abstractName);
            if (name != null)
                rule = context.getObjectByName(Rule.class, name);
        }
        return rule;
    }

    /**
     * Given a list of names, return a list of Rule objects.
     *
     * @param context    The SailPointContext to use.
     * @param something  A Collection or comma-separated string of names of rules.
     *
     * @ignore
     * Used by various objects that take lists of rules from task
     * arguments or configuration objects.
     */
    @SuppressWarnings("rawtypes")
    public static List<Rule> getRules(SailPointContext context,
                                      Object something)
        throws GeneralException {

        Collection names = null;
        if (something instanceof Collection)
            names = (Collection)something;
        else if (something != null) {
            // coerce to string, and allow it to be a CSV
            names = Util.csvToList(something.toString());
        }

        List<Rule> rules = null;
        if (names != null) {
            for (Object o : names) {
                Rule rule = null;
                if (o instanceof Rule) {
                    // Allow this, though we shouldn't have references
                    // to Rules in the task argument maps?
                    rule = (Rule)o;
                }
                else if (o != null) {
                    String name = o.toString();
                    rule = context.getObjectByName(Rule.class, name);
                    if (rule == null)
                        throw new GeneralException("Unknown rule: " + name);

                    // TODO: pass in a Signature to validate that this
                    // is the right kind of rule
                }
                if (rule != null) {
                    if (rules == null)
                        rules = new ArrayList<Rule>();
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Filters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a Filter from the given object, which should either be a Filter
     * or a string that compiles into a Filter.
     *
     * @param  con        The SailPointContext to use.
     * @param  something  A Filter or String that compiles into a Filter.
     */
    static public Filter getFilter(SailPointContext con,
                                   Object something)
        throws GeneralException {

        Filter filter = null;

        if (something instanceof Filter)
            filter = (Filter)something;

        else if (something instanceof String)
            filter = Filter.compile((String)something);

        return filter;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Soft References
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lookup a SailPointObject given a "soft" reference.
     *
     * @ignore
     * Used in a few places like WorkItem where we need to be able
     * to be able to reference any class of object but we don't
     * have specific fields for each class.
     */
    @SuppressWarnings("unchecked")
    static public SailPointObject getObject(SailPointContext con,
                                            String className,
                                            String id)
        throws GeneralException {

        SailPointObject obj = null;

        if (className != null && id != null) {

            if (className.indexOf(".") < 0)
                className = "sailpoint.object." + className;

            try {
                Class<SailPointObject> cls =
                    (Class<SailPointObject>) Class.forName(className);
                obj = con.getObjectById(cls, id);
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }

        return obj;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Applications
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return whether the given attribute is modifiable in a remediation
     * request on the given application.
     */
    public static boolean isRemediationModifiable(SailPointContext ctx,
                                                  String appName,
                                                  String schemaName,
                                                  String attrName)
        throws GeneralException {

        boolean modifiable = false;

        AttributeDefinition attrDef =
            getAttributeDefinition(ctx, appName, schemaName, attrName);
        if (null != attrDef) {
            modifiable = attrDef.isRemediationModifiable();
        }

        return modifiable;
    }

    /**
     * Return whether permissions are modifiable in a remediation request
     * on the given application.
     */
    public static boolean isPermissionRemediationModifiable(SailPointContext ctx,
                                                            String appName,
                                                            String schemaName)
        throws GeneralException {

        boolean modifiable = false;

        Schema schema = getSchema(ctx, appName, schemaName);
        if (null != schema) {
            modifiable = schema.isPermissionRemediationModifiable();
        }

        return modifiable;
    }

    /**
     * Return the remediation modification input type for the given attribute
     * on the given application.
     */
    public static UserInterfaceInputType getRemediationInputType(SailPointContext ctx,
                                                                 String appName,
                                                                 String schemaName,
                                                                 String attrName)
        throws GeneralException {

        UserInterfaceInputType type = null;
        AttributeDefinition attrDef =
            getAttributeDefinition(ctx, appName, schemaName, attrName);
        if (null != attrDef) {
            type = attrDef.getRemediationModificationType();
        }
        return type;
    }

    /**
     * Return the remediation modification input type for the permissions
     * on the given application.
     */
    public static UserInterfaceInputType getPermissionRemediationInputType(
                                               SailPointContext ctx,
                                               String appName,
                                               String schemaName)
        throws GeneralException {

        UserInterfaceInputType type = null;
        Schema schema = getSchema(ctx, appName, schemaName);
        if (null != schema) {
            type = schema.getPermissionsRemediationModificationType();
        }
        return type;
    }

    /**
     * Return the schema with the given name - or the account schema if a name
     * is not specified - from the requested application.
     */
    private static Schema getSchema(SailPointContext ctx, String appName, String schemaName)
        throws GeneralException {

        Schema schema = null;

        Application app = ctx.getObjectByName(Application.class, appName);
        if (null != app) {
            if (null == schemaName) {
                schema = app.getSchema(Application.SCHEMA_ACCOUNT);
            } else {
                schema = app.getSchema(schemaName);
            }
        }

        return schema;
    }

    private static AttributeDefinition getAttributeDefinition(SailPointContext ctx,
                                                              String appName,
                                                              String schemaName,
                                                              String attrName)
        throws GeneralException {

        AttributeDefinition attrDef = null;
        Schema schema = getSchema(ctx, appName, schemaName);
        if (null != schema) {
            attrDef = schema.getAttributeDefinition(attrName);
        }

        return attrDef;
    }

    /**
     * Return the possible rights for the permissions of the given target on
     * the given application.
     *
     * NOTE: THIS DOESN'T REALLY LOOK ON THE APPLICATION YET SINCE RIGHTS ARE
     * NOT SEARCHABLE, INSTEAD IT JUST CONSULTS THE RIGHT CONFIG OBJECT.
     *
     * @param  ctx         The context to use.
     * @param  locale      The Locale to use.
     * @param  appName     The name of the application.
     * @param  targetName  The name of the permission target.
     * @param  prefix      The optional prefix of the right name.
     * @param  start     The zero-based starting index for results.
     * @param  limit       The maximum number of results to return.
     */
    public static List<Object> getLinkPermissionRights(SailPointContext ctx,
                                                       final Locale locale,
                                                       String appName,
                                                       String targetName,
                                                       String prefix, int start,
                                                       int limit)
        throws GeneralException {

        List<Object> values = new ArrayList<Object>();

        // For now we'll pull from right config.  Eventually, we should probably
        // make rights searchable and use something like getLinkAttributeValues.
        RightConfig config =
            ctx.getObjectByName(RightConfig.class, RightConfig.OBJ_NAME);
        if (null != config) {
            List<Right> rights = config.getRights();
            if (null != rights) {
                // Copy the list and sort it.
                rights = new ArrayList<Right>(rights);
                Collections.sort(rights, new Comparator<Right>() {
                    public int compare(Right o1, Right o2) {
                        String r1 = (null != o1) ? o1.getName() : null;
                        String r2 = (null != o2) ? o2.getName() : null;
                        return Util.nullSafeCompareTo(r1, r2);
                    }
                });

                // Get the rights that start with the given prefix.
                for (Right right : rights) {
                    String name = right.getName();
                    if ((null == prefix) ||
                        name.toUpperCase().startsWith(prefix.toUpperCase())) {
                        values.add(right.getName());
                    }
                }

                // Now trim is down to a max size of limit.
                rights.subList(start, Math.min(start+limit, rights.size()));
            }
        }

        return values;
    }
    
    /**
     * Return the ID of the Identity that has a Link on the given application
     * with the given nativeId.
     */
    public static String getIdentityFromLink(SailPointContext ctx, Application app, String instance, String nativeId) throws GeneralException {
        String identityId = null;
        
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("application", app));
        if (null != instance) qo.addFilter(Filter.eq("instance", instance));
        qo.addFilter(Filter.ignoreCase(Filter.eq("nativeIdentity", nativeId)));

        int identCount = ctx.countObjects(Link.class, qo);
        // There should be only one identity that has this link
        if (1 == identCount) {
             Iterator<Object[]> ids = ctx.search(Link.class, qo, Arrays.asList("identity.id"));
             identityId = (String) ids.next()[0];
        }
        
        return identityId;
    }

    /**
     * Return the possible values for the given attribute on the given
     * application.
     *
     * NOTE: This requires that the attribute be configured as a searchable
     * (or multi-valued) link attribute.
     *
     * @param  ctx       The context to use.
     * @param  appName   The name of the application.
     * @param  attrName  The name of the attribute.
     * @param  prefix    The optional prefix of the values to search for.
     * @param  start     The zero-based starting index for results.
     * @param  limit     The maximum number of results to return.
     */
    public static List<Object> getLinkAttributeValues(SailPointContext ctx,
                                                      String appName,
                                                      String attrName,
                                                      String prefix, int start,
                                                      int limit)
        throws GeneralException {

        List<Object> values = null;

        Application app = ctx.getObjectByName(Application.class, appName);
        if (null != app) {

            ObjectConfig linkConfig = Link.getObjectConfig();
            String linkConfigAttrName = ObjectUtil.getLinkConfigAttributeName(linkConfig, appName, attrName);

            ObjectAttribute objAttr = null;

            // if we did not override, then find based on the source definitions
            // if we did override, just fetch the attr by name
            if ( linkConfigAttrName == null ) {
                objAttr = linkConfig.getObjectAttributeWithSource(app, attrName);
            } else {
                if (log.isDebugEnabled())
                    log.debug("Overriding link attribute mapping sources and using " +
                              linkConfigAttrName + " for " + appName + ":" + attrName);
                
                objAttr = linkConfig.getObjectAttribute(linkConfigAttrName);
            }

            if (null == objAttr) {
                if (log.isWarnEnabled()) {
                    String msg = "Unable to find link attribute for " + appName + ":" + attrName;
                    if ( linkConfigAttrName != null ) 
                        msg += " (" + linkConfigAttrName + ")";
                    
                    log.warn(msg);
                }
            }
            else {
                if (objAttr.isMulti()) {
                    values = getExternalLinkAttrValues(ctx, app, objAttr.getName(), prefix, start, limit);
                }
                else if (objAttr.isSearchable()) {
                    values = getSearchableLinkAttrValues(ctx, app, objAttr.getName(), prefix, start, limit);
                }
                else {
                    if (log.isWarnEnabled())
                        log.warn("Trying to search for attribute values for non-searchable " +
                                 "link attribute - " + appName + ":" + attrName);
                }
            }
        } else {
            if (log.isWarnEnabled())
                log.warn("Unable to find application " + appName + 
                         " to determine link attribute values.");
            
            return values;
        }

        return values;
    }

    /**
     * Return the number of possible values for the given attribute on the given
     * application.
     *
     * NOTE: This requires that the attribute be configured as a searchable
     * (or multi-valued) link attribute.
     *
     * @param  ctx       The context to use.
     * @param  appName   The name of the application.
     * @param  attrName  The name of the attribute.
     * @param  prefix    The optional prefix of the values to search for.
     */
    public static int countLinkAttributeValues(SailPointContext ctx,
            String appName,
            String attrName,
            String prefix)
        throws GeneralException {

        ObjectConfig linkConfig = Link.getObjectConfig();
        Application app = ctx.getObjectByName(Application.class, appName);
        if (app == null) {
            if (log.isWarnEnabled())
                log.warn("null application: " + appName);
            
            return 0;
        }

        //check for the attribute values override
        String linkConfigAttrName = ObjectUtil.getLinkConfigAttributeName(linkConfig, appName, attrName);

        ObjectAttribute objAttr = null;
        // if we did not override, then find based on the source definitions
        // if we did override, just fetch the attr by name
        if ( linkConfigAttrName == null ) {
            objAttr = linkConfig.getObjectAttributeWithSource(app, attrName);
        } else {
            if (log.isDebugEnabled())
                log.debug("Overriding link attribute mapping sources and using " +
                          linkConfigAttrName + " for " + appName + ":" + attrName);
            
            objAttr = linkConfig.getObjectAttribute(linkConfigAttrName);
        }

        if (null == objAttr) {
            if (log.isWarnEnabled())
                log.warn("Trying to search for attribute values for non link attribute - " +
                         appName + ":" + attrName);
            return 0;
        }

        if (objAttr.isMulti()) {
            return countExternalLinkAttrValues(ctx, app, objAttr.getName(), prefix);
        }
        else if (objAttr.isSearchable()) {
            return countSearchableLinkAttrValues(ctx, app, objAttr.getName(), prefix);
        }
        else {
            if (log.isWarnEnabled())
                log.warn("Trying to search for attribute values for non-searchable " +
                         "link attribute - " + appName + ":" + attrName);
            return 0;
        }

    }

    /**
     * Check for the custom link attribute mapping of this application and attribute
     * @param  linkConfig The ObjectConfig to use.
     * @param  appName   The name of the application.
     * @param  attrName  The name of the attribute.
     *
     * @return Link attribute name to use for values, or null if no mapping exists.
     */
    private static String getLinkConfigAttributeName(ObjectConfig linkConfig,
            String appName,
            String attrName) {

        String linkConfigAttrName = null;
        Object mapObject = linkConfig.get("customAttributeMap");
        if ( mapObject != null ) {
            if ( mapObject instanceof Map ) {
                Object attrMap = ((Map)mapObject).get(appName);
                if ( attrMap != null ) {
                    if ( attrMap instanceof Map ) {
                        Object value = ((Map)attrMap).get(attrName);
                        if ( value != null )
                            linkConfigAttrName = value.toString();
                    } else {
                        if (log.isWarnEnabled())
                            log.warn("Link ObjectConfig contains a customAttributeMap " +
                                     "entry for " + appName + " that is not a Map." );
                    }
                }
            } else {
                log.warn("Link ObjectConfig contains a customAttributeMap entry that is not a Map." );
            }
        }

        return linkConfigAttrName;

    }

    /**
     * Search for external link attributes on the given application attribute.
     */
    private static List<Object> getExternalLinkAttrValues(SailPointContext ctx,
                                                          Application app,
                                                          String attrName,
                                                          String prefix,
                                                          int start, int limit)
        throws GeneralException {

        QueryOptions qo = getExternalLinkAttrQueryOptions(app, attrName);
        return getLinkAttrValues(ctx, LinkExternalAttribute.class, qo,
                                 "value", prefix, start, limit);
    }

    private static int countExternalLinkAttrValues(SailPointContext ctx,
                                                          Application app,
                                                          String attrName,
                                                          String prefix)
        throws GeneralException {

        QueryOptions qo = getExternalLinkAttrQueryOptions(app, attrName);
        return countLinkAttrValues(ctx, LinkExternalAttribute.class, qo,
                                   "value", prefix);
    }

    private static QueryOptions getExternalLinkAttrQueryOptions(Application app,
                                                                String attrName) {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.join("objectId", "Link.id"));
        qo.add(Filter.eq("Link.application", app));
        qo.add(Filter.ignoreCase(Filter.eq("attributeName", attrName)));
        return qo;
    }

    /**
     * Search for link attributes on the given application attribute.
     */
    private static List<Object> getSearchableLinkAttrValues(SailPointContext ctx,
                                                            Application app,
                                                            String attrName,
                                                            String prefix,
                                                            int start, int limit)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("application", app));
        return getLinkAttrValues(ctx, Link.class, qo, attrName, prefix, start, limit);
    }

    private static int countSearchableLinkAttrValues(SailPointContext ctx,
                                                            Application app,
                                                            String attrName,
                                                            String prefix)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("application", app));
        return countLinkAttrValues(ctx, Link.class, qo, attrName, prefix);
    }

    /**
     * Search for attributes on the given application attribute.
     */
    private static List<Object> getLinkAttrValues(SailPointContext ctx,
                                                  Class<? extends SailPointObject> clazz,
                                                  QueryOptions qo, String attrName,
                                                  String prefix, int start, int limit)
        throws GeneralException {

        List<Object> values = new ArrayList<Object>();

        if (null != Util.getString(prefix)) {
            qo.add(Filter.ignoreCase(Filter.like(attrName, prefix, Filter.MatchMode.START)));
        }

        qo.setFirstRow(start);
        qo.setResultLimit(limit);
        qo.setDistinct(true);
        qo.addOrdering(attrName, true);

        List<String> props = new ArrayList<String>();
        props.add(attrName);

        Iterator<Object[]> results = ctx.search(clazz, qo, props);
        while (results.hasNext()) {
            values.add(results.next()[0]);
        }

        return values;
    }

    private static int countLinkAttrValues(SailPointContext ctx,
                                                  Class<? extends SailPointObject> clazz,
                                                  QueryOptions qo, String attrName,
                                                  String prefix)
        throws GeneralException {

        if (null != Util.getString(prefix)) {
            qo.add(Filter.ignoreCase(Filter.like(attrName, prefix, Filter.MatchMode.START)));
        }

        return countDistinctAttributeValues(ctx, clazz, qo, attrName);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Locking
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Default number of seconds waited before attempting to obtain a
     * persistent lock before throwing ObjectAlreadyLockedException.
     * Note that this MUST be less than the default amount of time
     * used when expiring locks, which is currently hard coded
     * in LockInfo at 5 minutes.  This may be overridden in sysconfig.
     */
    static public final int DEFAULT_LOCK_TIMEOUT = 60;

    /**
     * Calculate the amount of time to wait for a persistent lock.
     */
    static private int getLockTimeout(SailPointContext con)
        throws GeneralException {

        Configuration config = con.getConfiguration();
        int timeout = config.getInt(Configuration.LOCK_WAIT_TIMEOUT);
        if (timeout == 0)
            timeout = DEFAULT_LOCK_TIMEOUT;

        return timeout;
    }

    /**
     * Fetch an object in an appropriate way given LockParameters.
     * 
     * This is the preferred lockObject call method for ObjectUtil.
     * Other methods would call this.
     *
     * @ignore
     * Used by some background tasks that get lock modes passed to
     * them as arguments.  Hmm, need to fix the lockObject methods
     * so they can be called without locking.
     */
    static public <T extends SailPointObject> T lockObject(SailPointContext con,
                                                           Class<T> cls,
                                                           LockParameters params)
        throws GeneralException {

        T result = null;

        if (PersistenceManager.LOCK_TYPE_TRANSACTION.equals(params.getLockType())) {

            // this will automatically block
            result = lockObjectInternal(con, cls, params);
        }
        else if (PersistenceManager.LOCK_TYPE_PERSISTENT.equals(params.getLockType())) {

            // Have to implement the wait loop out here
            // since we wait 1 second between retries, lockTimeout is
            // actually treated as the "number of retries after the first attempt".
            // If it is zero we try once and give up.
            int tries = 0;
            while (tries <= params.getLockTimeout()) {
                try {
                    // after the first one wait a second between retries
                    if (tries > 0)
                        Util.sleep(1000);

                    result = lockObjectInternal(con, cls, params);
                    // may be null if the object was deleted
                    break;
                }
                catch (ObjectAlreadyLockedException e) {
                    tries++;
                }
            }

            if (tries > params.getLockTimeout()) {
                // TODO: The Exceptions we caught above included the
                // lockedBy name should we try to propagate that or
                // is it too much information?
                String msg = "Timeout waiting for lock on object: " +
                    cls.getName() + ":" + params.getValue();

                // jsl - temporary diagnostics for JPMC, add more info
                // about who locked the object, we should probably keep this
                // forever, but may want to adjust what the logging looks like
                // fetching the object would be the easiest but this is
                // a pretty big change to the session cache behavior I don't
                // want to risk
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq(params.getColumn(), params.getValue()));
                List<String> props = new ArrayList();
                props.add("lock");
                Iterator it = con.search(cls, ops, props);
                if (it.hasNext()) {
                    String lockString = (String)(((Object[])it.next())[0]);
                    LockInfo lock = new LockInfo(lockString);
                    String lockContext = lock.getContext();
                    if (lockContext != null) {
                        msg += " from " + lockContext;
                    }
                }

                // optional rule to perform possibly more expensive
                // diagnoses for Identity only
                if (cls == Identity.class) {
                    LockTracker.checkLockTimeoutRule(con, params.getColumn(), params.getValue());
                }
                
                // this has to throw or else Aggregator will think
                // it needs to create a new object
                throw new ObjectAlreadyLockedException(msg);
            }
            else {
                // remember identities
                if (result != null && cls == Identity.class) {
                    LockTracker.addLock(result.getName());
                }
            }
        }
        else {
            // a normal fetch
            if ("id".equals(params.getColumn()))
                result = con.getObjectById(cls, params.getValue());
            else
                result = con.getObjectByName(cls, params.getValue());
        }

        return result;
    }

    /**
     * This method will defer to @link #lockObject(SailPointContext, Class, LockParameters)
     */
    static public <T extends SailPointObject> T lockObject(SailPointContext con,
                                                           Class<T> cls,
                                                           String id,
                                                           String name,
                                                           String lockMode,
                                                           int lockTimeout)
        throws GeneralException {

        T result = null;
        
        LockParameters params = null;
        if (id != null) {
            params = LockParameters.createById(id, lockMode);
        } else {
            params = LockParameters.createByName(name, lockMode);
        }
        
        params.setLockTimeout(lockTimeout);

        result = lockObject(con, cls, params);
        
        return result;
    }

    /**
     * This method will defer to @link #lockObject(SailPointContext, Class, LockParameters)
     */
    static public <T extends SailPointObject> T lockObject(SailPointContext con,
                                                           Class<T> cls,
                                                           String id,
                                                           String name,
                                                           String lockMode)
        throws GeneralException {


        return lockObject(con, cls, id, name, lockMode, getLockTimeout(con));
    }

    static private <T extends SailPointObject> T lockObjectInternal(SailPointContext con,
                                                                    Class<T> cls,
                                                                    LockParameters params)
        throws GeneralException {

        T result = null;

        result = con.lockObject(cls, params);
        
        return result;
    }

    /**
     * Utility method to lock a certification by id.
     * This will retain a lock for a longer duration than other locks because
     * certification processing usually takes longer.
     * 
     * Currently it will use the value set in system configuration
     * value {@link Configuration#LONG_RUNNING_LOCK_TIMEOUT}. If that
     * value is missing it will use {@link LockInfo#DEFAULT_LONG_LOCK_TIMEOUT}
     *
     * @param context   The SailPoint context
     * @param id   The ID of the certification to lock.
     * @param lockTimeout How many seconds to try before giving up
     *
     * @return The locked certification or null if certification is already locked or lock cannot be obtained.
     */
    public static Certification lockCertificationById(SailPointContext context, String id, int lockTimeout) throws GeneralException {

        Certification certification = context.getObjectById(Certification.class, id);
        if (certification.isLocked()) {
            return null;
        } else {
            
            LockParameters params = LockParameters.createById(id, PersistenceManager.LOCK_TYPE_PERSISTENT);
            
            params.setLockTimeout(lockTimeout);

            int duration = Configuration.getSystemConfig().getInt(Configuration.LONG_RUNNING_LOCK_TIMEOUT);
            if (duration == 0) {
                duration = LockInfo.DEFAULT_LONG_LOCK_TIMEOUT; 
            }
            params.setLockDuration(duration);
            
            return ObjectUtil.lockObject(context, Certification.class, params);
        }
    }
    
    /**
     * Relock an object if the transaction was committed before this
     * is completed. This can happen in Aggregator/Identitizer where
     * it is hard to control when transactions are committed.
     *
     * @ignore
     * Sigh, I tried to avoid passing in both Class<T> and T object but
     * I couldn't find a way to get a Class<T> to pass to lockObject
     * from a T.
     */
    static public <T extends SailPointObject> T relockObject(SailPointContext con,
                                                             Class<T> cls,
                                                             T object,
                                                             String lockMode,
                                                             int lockTimeout)
        throws GeneralException {

        T locked = object;

        if (PersistenceManager.LOCK_TYPE_TRANSACTION.equals(lockMode) ||
            (PersistenceManager.LOCK_TYPE_PERSISTENT.equals(lockMode) &&
             object.getLock() == null)) {

            locked = lockObject(con, cls, object.getId(), object.getName(), lockMode, lockTimeout);

        }

        return locked;
    }

    /**
     * Unlock an object if it holds a persistent lock.
     * This must be used during error cleanup if you have been obtaining
     * persistent locks. If the this is a persistent lock the lock
     * will be removed and the transaction committed.
     * If this is a transasction lock, saveObject() will be called on the
     * object but the transaction is not committed.
     */
    static public void unlockObject(SailPointContext con,
                                    SailPointObject object,
                                    String lockMode)
        throws GeneralException {

        if (object != null) {
            if (PersistenceManager.LOCK_TYPE_TRANSACTION.equals(lockMode)) {
                con.commitTransaction();
            }
            else if (object.getLock() != null) {
                // this will commit
                con.unlockObject(object);
            }
        }
    }

    /**
     * Check whether the request object is locked by a persistent lock. This
     * does not check for transaction locks.
     *
     * @ignore
     * !!TQM. This method seems to be only checking for Identity.class.
     */
    public static boolean isLocked(SailPointContext con,
                                   Class<? extends SailPointObject> cls,
                                   String name)
        throws GeneralException {

        boolean locked = false;

        // Using a projection query and building the LockInfo here to avoid
        // loading the whole identity.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("name", name));
        Iterator<Object[]> it = con.search(Identity.class, qo, "lock");
        if (it.hasNext()) {
            String lock = (String) it.next()[0];

            if (it.hasNext()) {
                throw new GeneralException("Expected single identity: " + name);
            }

            if (null != lock) {
                LockInfo lockInfo = new LockInfo(lock);
                locked = !lockInfo.isExpired();
            }
        }

        return locked;
    }

    /**
     * Check whether the request object is locked by a persistent lock. This
     * does not check for transaction locks.
     */
    public static boolean isLockedById(SailPointContext con,
                                   Class<? extends SailPointObject> cls,
                                   String id)
        throws GeneralException {

        boolean locked = false;

        // Using a projection query and building the LockInfo here to avoid
        // loading the whole identity.
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("id", id));
        Iterator<Object[]> it = con.search(cls, qo, "lock");
        if (it.hasNext()) {
            String lock = (String) it.next()[0];

            if (it.hasNext()) {
                throw new GeneralException("Expected single object: " + id);
            }

            if (null != lock) {
                LockInfo lockInfo = new LockInfo(lock);
                locked = !lockInfo.isExpired();
            }
        }

        return locked;
    }
    
    /**
     * Lock the Identity with the given ID.
     *
     * @param ctx The SailPointContext
     * @param id Identity Id
     * @param timeout Timeout in seconds 0 for default
     * @return The locked identity
     */
    static public Identity lockIdentityById(SailPointContext ctx, String id, int timeout) 
        throws GeneralException {
        
        if (timeout == 0)
            timeout = getLockTimeout(ctx);
        
        if (log.isInfoEnabled()) {           
            log.info("Locking identity: " +  id + " timeout:" + timeout);             
        }  
        
        Identity resultId = lockObject(ctx, Identity.class, id, null, PersistenceManager.LOCK_TYPE_PERSISTENT, timeout);
        
        if (log.isInfoEnabled()) {           
            if (resultId != null) {
                log.info("Locked identity: " +  id + " timeout:" + timeout);
            }
            else {
                log.info("Unable to lock identity: " + id + " timeout:" + timeout);
            }
        }        

        return resultId;
    }

    /**
     * Lock an identity with the default lock timeout.
     *
     * @see #lockIdentityById(SailPointContext, String, int)
     */
    static public Identity lockIdentityById(SailPointContext ctx, String id) 
        throws GeneralException {

        return lockIdentityById(ctx, id, 0);
    }

    /**
     * Lock the Identity with the given name.
     *
     * @param ctx The SailPointContext
     * @param name Identity name
     * @param timeout Timeout in seconds 0 for default
     * @return The locked identity.
     */
    static public Identity lockIdentityByName(SailPointContext ctx, String name, int timeout) 
        throws GeneralException {
    
        if (timeout == 0)
            timeout = getLockTimeout(ctx);
        
        if (log.isInfoEnabled()) {
                log.info("Locking identity: " + name  + " timeout:" + timeout);
        }
        
        Identity resultId = lockObject(ctx, Identity.class, null, name, PersistenceManager.LOCK_TYPE_PERSISTENT, timeout);
        
        if (resultId != null && log.isInfoEnabled()) {
            log.info("Locked identity: " + resultId.getName() + " timeout:" + timeout);         
        }
        else if(log.isInfoEnabled()){
            log.info("Unable to lock identity: " + name + " timeout:" + timeout);
        }

        return resultId;
    }

    /**
     * Lock an identity with the default lock timeout.
     *
     * @see #lockIdentityByName(SailPointContext, String, int)
     */
    static public Identity lockIdentityByName(SailPointContext ctx, String name) 
        throws GeneralException {
    
        return lockIdentityByName(ctx, name, 0);
    }
    
    /**
     * Convenience method to lock an Identity object using a persistent lock.
     * The lock name will be a new unqiue identifier.
     * Code that calls should always continue with a try block with a finally
     * clause that calls unlockIdentity or releases the lock in some other way.
     *
     * This the recommended way for system code and custom code to lock identities
     * so unique persistent locks can be used consistently.
     *
     * @param con  The SailPointContext to use.
     * @param identifier  The ID or name of the identity to lock.
     * @param timeout  The timeout in seconds, 0 for default.
     *
     * @return The locked identity.
     */
    static public Identity lockIdentity(SailPointContext con, String identifier, int timeout)
        throws GeneralException {


        Identity result = null;

        if (isUniqueId(identifier)) {
            result = lockIdentityById(con, identifier, timeout);
            if (result == null) {
                result = lockIdentityByName(con, identifier, timeout);
            }
        } else {
            result = lockIdentityByName(con, identifier, timeout);
            if (result == null) {
                result = lockIdentityById(con, identifier, timeout);
            }
        }
        
        return result;
    }
    
    /**
     * Lock an identity with the default lock timeout.
     *
     * @see #lockIdentity(SailPointContext, String, int)
     */
    static public Identity lockIdentity(SailPointContext con, String identifier)
        throws GeneralException {

        return lockIdentity(con, identifier, 0);
    }

    /**
     * Lock an identity with the default lock timeout.
     *
     * @see #lockIdentity(SailPointContext, String, int)
     */
    static public Identity lockIdentity(SailPointContext con, Identity src)
        throws GeneralException {

        return lockIdentityById(con, src.getId(), 0);
    }

    
    /**
     * Unlock an identity previously locked by lockIdentity.
     * The equivalent of saveObject will be called on the identity
     * and the transaction will be committed.
     */
    static public void unlockIdentity(SailPointContext con, Identity id)
        throws GeneralException {

        if (id != null && id.getLock() != null) {

            if (log.isInfoEnabled()) {
                // Be consistent, provide the id() field like the lockers do.
                log.info("Unlocking identity: " + id.getId() + " [" + id.getName() + "]");
            }

            // not using SailPointContext.unlockObject here because it is 
            // ambiguous whether it will commit which makes it useless
            // rethink this...

            id.setLock(null);
            con.saveObject(id);
            con.commitTransaction();
        }
    }

    /**
     * Obtain a transaction lock.
     * This should now never be used for identities, always use persistent
     * locks for identities.
     */
    static public <T extends SailPointObject> T transactionLock(SailPointContext con,
                                                                Class<T> cls,
                                                                String identifier)
        throws GeneralException {

        T result = null;

        Map<String,Object> options = new HashMap<String,Object>();
        options.put(SailPointContext.LOCK_TYPE, PersistenceManager.LOCK_TYPE_TRANSACTION);

        if (isUniqueId(identifier))
            result = con.lockObjectById(cls, identifier, options);
        else
            result = con.lockObjectByName(cls, identifier, options);

        return result;
    }

    /**
     * Lock an identity but remember if the lock was already held by this thread.
     * Used only by the Terminator when deleting things that require an update to 
     * an Identity.  Terminator can be used deep inside a thread that may have already
     * aquired a lock on an object.  If this thread already owns the lock we have to
     * remember that so that unlockIfNecessary can avoid clearing the lock.  
     *
     * This must be followed by a try/finally block that calls unlockIfNecessary.
     *
     * This one is complicated so pay close attention. Bug 26142 involved
     * an experimental feature that attempted to catch accidental overwriting of 
     * persistent Identity locks.  It did so by adding the concept of a single
     * automatically generated lock name for each thread that would be used instead
     * of a manually generated lock name that tasks were then using.  With that,
     * logging was added to SailPointInterceptor if an object was flushed without
     * using the thread lock name.  
     *
     * Part of that also changed the Terminator so that as part of deleting
     * an Identity, if it had to touch any other Identity (for example a workgroup
     * identity's membership list) it would lock and unlock that other identity.
     * There was fear though that if that other identity had already been locked by
     * this thread, unlocking would lose the lock.  It was impossible for Terminator
     * to know if the thread already had the lock since lock names were arbitrary 
     * and could not be passed down through all the levels to the Terminator call.
     * 
     * When the EnableThreadLockConsistencyCheck flag was on though, we would (mostly)
     * use the auto-generated lock name so we could lock/unlock safely.  However since
     * this flag was only used in a few test conditions trying to chase down a lock 
     * conflict it was never on by default so in effect Terminator has never actually
     * been doing locking.  
     *
     * When I removed user generated lock names in 7.3, and fixed the setting
     * of the refreshedExistingLock flag, we were in a position to enable locking 
     * within the Terminator.  This is arguably the right thing to do, though having
     * examined the places that delete identities in agg/refresh I doubt there will
     * be any cases where the thread already owns the lock, so the "IfNecessary" part
     * is probably not justified.  
     *
     * Note that this does result in a significant change in behavior.  Before
     * we would always just call getObject instead of locking which would return what
     * was in the cache.  Now we will refresh the lock and fetch a new object so 
     * uncommitted changes could be lost.  I reviewed the areas that delete identities
     * and do not believe this to be a problem.  
     *
     * An alternative implementation would be to bring the lock checking up here
     * instead of down in HibernatePersistenceManager and just avoid the refresh
     * if we see that we already have the lock.  That's more like how it used to work
     * and would make the use of the mysterious refreshedExistingLock flag more 
     * obvious.
     */
    static public Identity lockIfNecessary(SailPointContext con, String idOrName)
        throws GeneralException {

        // unconditional as of 7.3, HibernatePersistenceManager will set the
        // refreshedExistingLock flag on the returned object
        return lockIdentity(con, idOrName);
    }

    /**
     * Must be called by anything calling lockIfNecessary in a finally block after
     * modifying the identity.
     */
    static public void unlockIfNecessary(SailPointContext con, Identity ident)
        throws GeneralException {

        if (!ident.isRefreshedExistingLock()) {
            unlockIdentity(con, ident);
        }
        else {
            // already locked by this thread, just save it
            con.saveObject(ident);
            con.commitTransaction();
        }
    }

    /**
     * Break a lock held on an object.
     * This is intended only for the unit tests which occasionally need to clean
     * up from previous tests that left locks behind.  
     */
    static public <T extends SailPointObject> void breakLock(SailPointContext con, Class<T> cls, String id)
        throws GeneralException {

        T obj = con.getObjectById(cls, id);
        if (obj != null) {
            breakLock(con, obj);
        }
    }
    
    /**
     * Break a lock held on an object.
     * This is intended only for the unit tests which occasionally need to clean
     * up from previous tests that left locks behind.  
     */
    static public <T extends SailPointObject> void breakLock(SailPointContext con, T obj)
        throws GeneralException {

        if (obj != null && obj.getLock() != null) {
            log.warn("Breaking lock on " + obj.getClass().getSimpleName() + ":" + obj.getName());
            // If lock monitoring is enabled have to disable that so we don't get
            // an exception thrown.  This really should be pushed down into PersistenceManager.
            boolean previous = sailpoint.persistence.HibernatePersistenceManager.EnableThreadLockConsistencyCheck;
            try {
                sailpoint.persistence.HibernatePersistenceManager.EnableThreadLockConsistencyCheck = false;
                obj.setLock(null);
                con.saveObject(obj);
                con.commitTransaction();
            }
            finally {
                sailpoint.persistence.HibernatePersistenceManager.EnableThreadLockConsistencyCheck = previous;
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Misc
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Take a Collection of SailPointObjects and return a map that can look up the objects by ID.
     * @param objectsToMap Collection of SailPointObjects that need to be mapped by ID
     * @return Map of SailPointObjects keyed by their IDs
     */
    static public Map<String, ? extends SailPointObject> getObjectsMappedById(Collection<? extends SailPointObject> objectsToMap) {
        final Map<String, SailPointObject> mappedObjs = new HashMap<String, SailPointObject>();

        if (null != objectsToMap && !objectsToMap.isEmpty()) {
            for (SailPointObject obj : objectsToMap) {
                mappedObjs.put(obj.getId(), obj);
            }
        }

        return mappedObjs;
    }

    /**
     * This method handles the meta-data storage when editing
     * object attributes that are declared editable. It should be
     * called by any components wishing to manually update an
     * attribute if the manual value should persist after
     * future aggregations. This method only supports Identity
     * and Link objects right now.
     */
    public static void editObjectAttribute(SailPointObject object,
                                           ObjectAttribute attr,
                                           Object newValue, String user)
        throws GeneralException {

        if ( object != null ) {
            String attrName = attr.getName();
            if ( !attr.isEditable() ) {
                //TODO: i18n
                throw new GeneralException("Attempting to edit attribute ["+attrName+"] when its not marked editable.");
            }

            AttributeMetaData meta = object.getAttributeMetaData(attrName);
            if ( meta == null ) {
                Object orig = getObjectAttributeValue(object, attr);
                String origStrValue = null;
                if (orig != null)  {
                    origStrValue = orig.toString();
                }
                meta = new AttributeMetaData(attrName, user, origStrValue);
                object.addAttributeMetaData(meta);
            }
            else if (meta.getUser() != null) {
                // Leave the value as is on the meta data because we want
                // the original value to be stored.  However, update the user
                // and last modified.
                // jsl - I don't understand this...why isn't this the same
                // as the clause below...please explain...
                meta.setUser(user);
                meta.incrementModified();
            }
            else {
                // This is here holding the source we used for a multli-source
                // attribute.  This gets promoted to hold manual editing state.
                meta.setUser(user);
                meta.setSource(null);
                Object attrValue = getObjectAttributeValue(object, attr);
                meta.setLastValue(Util.otoa(attrValue));
            }
            
            // would be nice if we had defined setAttribute/getAttribute
            // on SailPointObject but it's too late now
            if ( object instanceof Identity ) {
                Identity id = (Identity)object;
                id.setAttribute(attrName, newValue);
            }
            else if ( object instanceof Link ) {
                Link link = (Link)object;
                link.setAttribute(attrName, newValue);
            }
        }
    }

    /**
     * This method pulls attribute values out of Identity and Link objects.
     * This is required because there is some special-casing around the
     * manager attribute for Identities.
     * @param object  The Identity or Link to get the value from.
     * @param attr ObjectAttribute (attribute definition) for the attribute being retrieved
     * @return The attribute value
     */
    public static Object getObjectAttributeValue(SailPointObject object, ObjectAttribute attr) {

        Object value = null;
        String attname = attr.getName();

        if (object instanceof Identity)  {
            Identity id = (Identity)object;
            if ( attname.compareTo(Identity.ATT_MANAGER) == 0 ) {
                // unfortunatly the getAttribute method returns
                // the name of the identity instead of the actual
                // object.
                value = id.getManager();
                if ( value != null ) {
                    Identity manager = (Identity)value;
                    //Why are we storing arbitrary object values - here we used to use
                    //getId, in the provisioner we use getName.  getName is the contract now dang it.
                    value = manager.getName();
                }
            } 
            else {
                // this will also handle extended ideneity attributes
                value = id.getAttribute(attname);
            }
        } 
        else if (object instanceof Link) {
            Link link = (Link)object;
            value = link.getAttribute(attname);
        }

        return value;
    }

    /**
     * Verifies that all required attributes have a value. Also verifies that required String attributes are not blank.
     * @param attributes Map of attribute values keyed by attribute name
     * @param validAttributeDefinitions list of attributes that are valid for this role
     * @param config Role Configuration
     * @param locale Logged in user's locale
     * @return List of attribute names for any attributes that do not have values or (in the case of Strings) have blank values.
     * If no such attributes exist or if the attributes list was null an empty list is returned
     */
    public static List<String> getInvalidAttributes(Attributes<String, Object> attributes, List<ObjectAttribute> validAttributeDefinitions, ObjectConfig config, Locale locale) {
        List<String> invalidAttributes = new ArrayList<String>();
        Map<String, ObjectAttribute> attributeMap = config.getObjectAttributeMap();
        if (attributes != null && !attributes.isEmpty()) {
            Set<String> attributeNames = attributes.keySet();
            for (String attributeName : attributeNames) {
                ObjectAttribute attributeDefinition;
                if (validAttributeDefinitions != null) {
                    attributeDefinition = findAttributeDefintionByName(validAttributeDefinitions, attributeName);
                } else {
                    attributeDefinition = attributeMap.get(attributeName);
                }
                // jsl - on occasion we put undefined attributes in here that won't have definitions
                if (attributeDefinition != null) {
                    if (attributeDefinition.isRequired()) {
                        Object attributeValue = attributes.get(attributeName);
                        if (attributeValue == null) {
                            invalidAttributes.add(attributeDefinition.getDisplayableName(locale));
                        } else if (attributeValue instanceof String && ((String) attributeValue).trim().length() == 0) {
                            invalidAttributes.add(attributeDefinition.getDisplayableName(locale));
                        }
                    }
                }
            }
        }

        return invalidAttributes;
    }

    private static ObjectAttribute findAttributeDefintionByName(List<ObjectAttribute> attributeDefinitions, String name) {
        for (ObjectAttribute attributeDefinition : attributeDefinitions) {
            if (name.equals(attributeDefinition.getName())) {
                return attributeDefinition;
            }
        }
        return null;
    }

    /**
     * Get the location of the temp dir. Default to java.io.tmpdir
     * but also check the system configuration
     * {@link sailpoint.object.Configuration#TEMP_DIR} for an override.
     */
    public static String getTempDir(SailPointContext ctx) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        try {
            Configuration sysConfig = ctx.getConfiguration();
            if ( sysConfig != null ) {
                String tmp = sysConfig.getString(Configuration.TEMP_DIR);
                if ( (tmp != null ) && ( tmp.length() > 0 ) ) {
                    tmpDir = tmp;
                }
            }
       } catch (GeneralException e) {
            log.error(e.getMessage(), e);
        }
        return tmpDir;
   }

    /**
     * Given a name or ID attempt to retrieve the matching workgroup or identity.
     */
    public static Identity getIdentityOrWorkgroup(SailPointContext ctx, String idOrName) throws GeneralException{
       Filter nameOrIdFilter = Filter.or(Filter.eq("id", idOrName),
                Filter.eq("name", idOrName));

        QueryOptions ops = new QueryOptions(nameOrIdFilter);
        ops.add(buildWorkgroupInclusiveIdentityFilter());
        List<Identity> results = ctx.getObjects(Identity.class, ops);
        return !results.isEmpty() ? results.get(0) : null;
    }

    /**
     * Return a filter that will cause a query against Identities to return
     * workgroups also.
     *
     * @ignore
     * Hibernate perisistence manager 'magically' adds 
     * workgroup == false to filters which don't have these.
     */
    public static Filter buildWorkgroupInclusiveIdentityFilter() {

        return Filter.or(Filter.eq("workgroup", true), Filter.eq("workgroup", false));
    }

   /**
    * Given an Identity that is a workgroup, return an iterator with the
    * requested properties of a all groups members.
    */
   public static Iterator<Object[]> getWorkgroupMembers(SailPointContext ctx,
                                                        Identity identity,
                                                        List<String> props )
       throws GeneralException {

       List<Identity> workgroups = new ArrayList<Identity>();
       workgroups.add(identity);
       QueryOptions ops = new QueryOptions();
       ops.add(Filter.containsAll("workgroups",workgroups));
       return ctx.search(Identity.class, ops, props);
   }

   /**
    * Return a List of email addresses to use to notify the given Identity,
    * which could be either an identity or workgroup. If this is a workgroup,
    * the workgroup notification option is consulted to determine the emails
    * that should be notified.
    */
   public static List<String> getEffectiveEmails(SailPointContext ctx,
                                                 Identity identity )
       throws GeneralException {

        List<String> emailAddresses = new ArrayList<String>();
        String thisEmail = Util.getString(identity.getEmail());
        if ( identity.isWorkgroup() ) {
            WorkgroupNotificationOption op = identity.getNotificationOption();
            if ( op == null )
                op = WorkgroupNotificationOption.Both;

            if ( ( op.equals(WorkgroupNotificationOption.MembersOnly) ) ||
                 ( op.equals(WorkgroupNotificationOption.Both) ) ) {
                // projection query
                List<Identity> workgroups = new ArrayList<Identity>();
                workgroups.add(identity);

                List<String> props = new ArrayList<String>(Arrays.asList(new String[]{"name","email"}));
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.containsAll("workgroups",workgroups));
                Iterator<Object[]> it = ctx.search(Identity.class, ops, props);
                if ( it != null ) {
                    while( it.hasNext() ) {
                        Object[] row = it.next();
                        if ( row != null ) {
                            if ( row.length == 1 ) {
                                if (log.isWarnEnabled()) {
                                    String name = (String)row[0];
                                    log.warn("Identity [" + name + "] does not have an " + 
                                             "email address and will not be notified.");
                                }
                            } else
                            if ( row.length == 2 ) {
                                String email = (String)row[1];
                                if ( Util.isNotNullOrEmpty(email) )
                                    emailAddresses.add(email);
                            }
                        }
                    }
                }
            }
            if ( ( op.equals(WorkgroupNotificationOption.GroupEmailOnly) ) ||
                 ( op.equals(WorkgroupNotificationOption.Both) ) )  {
                if ( Util.isNotNullOrEmpty(thisEmail) )
                    emailAddresses.add(thisEmail);
            }
        } else {
            if ( Util.isNotNullOrEmpty(thisEmail) )
                emailAddresses.add(thisEmail);
        }
        return ( Util.size(emailAddresses) > 0 ) ? emailAddresses : null;
    }

    /**
     * Get the name for the given object id.
     * @param ctx A SailPointContext
     * @param clazz Class of object to retrive
     * @param id ID of object to retrieve
     * @return Object name
     * @throws GeneralException
     */
    public static String getName(SailPointContext ctx, Class clazz, String id) throws GeneralException{

        Iterator<Object[]> results = ctx.search(clazz, new QueryOptions(Filter.eq("id", id)), Arrays.asList("name"));
        if (results != null && results.hasNext()){
            return (String)results.next()[0];
        }
        return null;
    }

    /**
     * Get the ID for the given object name.
     * @param ctx A SailPointContext
     * @param clazz Class of object to retrieve
     * @param name Name of object to retrieve
     * @return Object ID
     * @throws GeneralException
     */
    public static String getId(SailPointContext ctx, Class clazz, String name) throws GeneralException{

        Iterator<Object[]> results = ctx.search(clazz, new QueryOptions(Filter.eq("name", name)), Arrays.asList("id"));
        if (results != null && results.hasNext()){
            return (String)results.next()[0];
        }
        return null;
    }

    /**
     * Converts the list of object IDs or names into the names of the respective objects
     * TODO: How do we handle this? -rap
     * @param ctx A SailPointContext
     * @param clazz Class of object to retrieve
     * @param idsOrNames ID or name of object to retrieve
     * @return List of object names
     */
    @SuppressWarnings("unchecked")
    public static List<String> convertToNames(SailPointContext ctx, Class clazz, List<String> idsOrNames) {
        List<String> names = new ArrayList<String>();

        if (idsOrNames != null && !idsOrNames.isEmpty()){
            // Handle dbs like sql server that only allow 2100 parameters in their 'in' query
            List<List<String>> partitions = Util.partition(idsOrNames, MAX_IN_QUERY_SIZE);
            for(List<String> partition : partitions) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.or(Filter.in("id", partition), Filter.in("name", partition)));
                // bug 19547 - hack to add workgroups flag to identities
                if (clazz.equals(Identity.class)) {
                    ops.add(Filter.in("workgroup", Arrays.asList(true, false)));
                }
                Iterator<Object[]> results = null;
                try {
                    results = ctx.search(clazz, ops, "id,name");
                } catch (GeneralException e) {
                    // KG - Why are we swallowing this?  Shouldn't it be up to the caller to decide to ignore or not?
                    log.error(e.getMessage(), e);
                }

                if (results != null) {
                    Map<String, String> namesById = new HashMap<>();
                    Set<String> allNames = new HashSet<>();

                    while (results.hasNext()) {
                        Object[] row = results.next();
                        String id = (String) row[0];
                        String name = (String) row[1];
                        namesById.put(id, name);
                        allNames.add(name);
                    }

                    // We need to return the names in the same order that they were requested in idsOrNames.
                    for (String idOrName : idsOrNames) {
                        String name = namesById.get(idOrName);

                        // If the idOrName was an ID.  The name should be in our Map if we could load it.
                        if (null != name) {
                            names.add(name);
                        } else {
                            // We didn't find it in the Map, so either the idOrName was an ID that we couldn't
                            // load OR it was a name.  In the first case, we'll ignore it.  In the second case,
                            // we'll make sure that we were able to find an object with the name before we add
                            // it to the list.
                            if (allNames.contains(idOrName)) {
                                names.add(idOrName);
                            }
                        }
                    }
                }
            }

            if (names.size() != idsOrNames.size()) {
                log.info("Unable to get all object names for: " + idsOrNames + ", got names:" + names);
            }
        }

        return names;
    }
    
    /**
     * Converts the list of object IDs or names into the ID of the respective objects
     * @param ctx A SailPointContext
     * @param clazz Class of object to retrieve
     * @param names ID or name of object to retrieve
     * @return List of object IDs
     */
    @SuppressWarnings("unchecked")
    public static List<String> convertToIds(SailPointContext ctx, Class clazz, List<String> names) {
        List<String> ids = new ArrayList<String>();

        if (names != null && !names.isEmpty()){
            // Handle dbs like sql server that only allow 2100 parameters in their 'in' query
            List<List<String>> partitions = Util.partition(names, MAX_IN_QUERY_SIZE);
            for(List<String> partition : partitions) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.or(Filter.in("id", partition), Filter.in("name", partition)));
                // bug 19547 - hack to add workgroups flag to identities
                if (clazz.equals(Identity.class)) {
                    ops.add(Filter.in("workgroup", Arrays.asList(true, false)));
                }
                Iterator<Object[]> results = null;

                try {
                    results = ctx.search(clazz, ops, Arrays.asList("id"));
                } catch (GeneralException e) {
                    log.error(e.getMessage(), e);
                }
                if (results != null) {
                    while (results.hasNext()) {
                        Object[] row = results.next();
                        ids.add((String) row[0]);
                    }
                }
            }
            
            if (names.size() != ids.size()) {
                log.info("Unable to get all object ids for: " + names + ", got ids:" + ids);
            }
        }

        return ids;
    }

    /**
     * Converts object ids to names.
     * Id value could be single id string, csv string, or list of strings.
     * 
     * @param context SailPointContext
     * @param clazz object class
     * @param value single id string, csv string, or list of strings
     * @return String or List of String based on the original type
     * @throws GeneralException
     */
    public static Object convertIdsToNames(SailPointContext context, Class<?> clazz, Object value) {
        if (value instanceof String) {
            List<String> names = convertToNames(context, clazz, Util.csvToList((String)value));
            if (!Util.isEmpty(names)) {
                value = Util.listToCsv(names);
            }
        } else if (value instanceof List<?>) {
            List<String> names = convertToNames(context, clazz, (List<String>)value);
            if (!Util.isEmpty(names)) {
                value = names;
            }
        }
        return value;
    }

    //
    // Secret Attribute Handling 
    //
    
    // Will likely need to expand these so they
    // look at the schema on application or form?
    // for now jsut look for a name that contains password
    /**
     * Return whether the given IdentityRequestItem contains a secret value.
     */
    public static boolean isSecret(IdentityRequestItem item ) {
        String name = (item != null) ? item.getName() : null;        
        return isSecret(name);
    }

    /**
     * Return whether the given name is the name of a secret attribute
     * @param name
     * @return
     */
    public static boolean isSecret(String name) {
        return ProvisioningPlan.isSecret(name);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Reflection
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the "major" SailPointObject class with the given simple name.
     */
    public static Class getMajorClass(String className){

        if (className == null)
            return null;

        String upname = className.toUpperCase();
        for(Class cls : ClassLists.MajorClasses){
            String base = cls.getSimpleName().toUpperCase();
            if (base.equals(upname)) {
                return cls;
            }
        }

        return null;
    }

    /**
     * Evaluates the class name. The simple class name
     * may be used, for example, Identity, Link, etc, or the
     * fully qualified class name.
     *
     * @return Class for the given name, or null if not found.
     */
    public static Class getSailPointClass(String className){

        if (className == null)
            return null;

        String upname = className.toUpperCase();
        for(Class cls : ClassLists.MajorClasses){
            String base = cls.getSimpleName().toUpperCase();
            if (base.equals(upname)) {
                return cls;
            }
        }

        if ("CertificationItem".equals(className))
            return CertificationItem.class;

        if ("Link".equals(className))
            return Link.class;

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
        }

        return null;
    }
    
    
    /**
     * Given a new list of permissions merge them into the currentPermissions  
     * list. This includes merging the rights all onto one Permissions and 
     * avoiding duplicates.
     */
    public static List<Permission> mergePermissions(List<Permission> currentPerms, 
                                                    List<Permission> newPerms ) {

        List<Permission> mergedList = new ArrayList<Permission>();
        if ( currentPerms!= null )
            mergedList = new ArrayList<Permission>(currentPerms);

        if ( ( currentPerms == null ) && ( newPerms != null ) ) {
            return new ArrayList<Permission>(newPerms);
        } 
        if ( newPerms == null ) {
            return currentPerms;
        }

        for ( Permission newPerm : newPerms ) {
            boolean targetFound = false;
            String newTarget = newPerm.getTarget();
            List<String> newRights = newPerm.getRightsList();
            for ( Permission perm : currentPerms ) {
                String target = perm.getTarget();
                if ( target != null ) {
                    if ( newTarget != null ) {
                        if ( target.compareTo(newTarget) == 0 ) {
                            targetFound = true;
                            List<String> currentRights = perm.getRightsList();
                            List<String> rights = new ArrayList<String>();
                            if ( currentRights != null ) {
                                rights = new ArrayList<String>(currentRights);
                            } else {
                                rights = new ArrayList<String>();
                            }
                            for ( String newRight : newRights ) {
                                if ( !rights.contains(newRight) ) {
                                    rights.add(newRight);
                                }
                            }
                            perm.setRights(Util.listToCsv(rights));
                            break;
                        }
                    } else {
                        log.warn("newTarget was returned null.");
                    }
                } else {
                    log.warn("ExistingTarget was returned null.");
                } 
            } 
            if ( !targetFound ) {
                // its a new one
                mergedList.add(newPerm);
            }
        }
        return mergedList;
    }

    /**
     * Check to see if a collection has been initialized.
     * Can be useful when making performance decisions of getting the
     * entire list of object or fetching them using a search.
     *
     * @ignore Calls to the static Hibernate.isInitialized method
     */
    public static boolean isInitialized(Object object) {
        return Hibernate.isInitialized(object);        
    }

    /**
     * Utility method which will create a persistent lock on the certifcation
     * while performing a task. Transaction will be committed on successful
     * completion if shouldUnlock is true.
     * 
     * @param context SailPointContext
     * @param certification the cert 
     * @param doWhat the task to perform. This task result is returned (see below).
     * @param shouldUnlock true if unlocking should be done after the task is finished. 
     * @param lockTimeout How long to wait for the lock
     * @return the first part of Pair object that is returned  is whether this method executed successfully, 
     *          the second part of the pair object is the result of running the task.
     */
    public static <T> Pair<Boolean, T> doWithCertLock(SailPointContext context, final Certification certification, Callable<T> doWhat, boolean shouldUnlock, int lockTimeout) throws GeneralException {

        Certification locked = lockCert(context, certification, lockTimeout);
        if (locked == null) {
            return new Pair<Boolean, T>(false, null);
        }
        
        // locked the cert so need unlocking.
        T returnValue = null;
        try {
            
            // perform the task
            returnValue = doWhat.call();

            return new Pair<Boolean, T>(true, returnValue);

        } catch (Throwable ex) {
            if (log.isErrorEnabled()) {
                log.error(ex);
            }
            throw new GeneralException("Exception while locking", ex);
        } finally {
            // unlock it
            if (shouldUnlock) {
                unlockCert(context, locked);
            }
        }
    }    

    /**
     * Lock the given certification with the specified timeout.
     */
    public static Certification lockCert(SailPointContext context, final Certification certification, int lockTimeout) throws GeneralException {

        if (certification == null) {
            if (log.isInfoEnabled()) {
                log.info("Cert is null");
            }
            return null;
        }
        if (certification.isLocked()) {
            if (log.isInfoEnabled()) {
                log.info(String.format("Cert with id %s: is already locked.", certification.getId()));
            }
            return null;
        }
        
        try {

            context.decache();
            
            
            Certification lockedCert = ObjectUtil.lockCertificationById(context, certification.getId(), lockTimeout);
            if (lockedCert == null) {
                if (log.isInfoEnabled()) {
                    log.info(String.format("Could not lock cert with id %s.", certification.getId()));
                }
            }

            return lockedCert;
        } catch (ObjectAlreadyLockedException ex) {
            if (log.isInfoEnabled()) {
                log.info(String.format("Could not lock cert with id %s.", certification.getId()));
            }
            return null;
        } catch (Throwable ex) {
            if (log.isErrorEnabled()) {
                log.error(ex);
            }
            return null;
        } 
    }

    /**
     * Unlock the given certification.
     */
    public static void unlockCert(SailPointContext context, final Certification certification) throws GeneralException {
        
        if (certification == null) {
            if (log.isInfoEnabled()) {
                log.info("nothing to unlock");
            }
            return;
        }

        //Need to fetch the latest version
        //first commit uncommitted, then decache in order to load fresh
        context.commitTransaction();
        context.decache();

        // then unlock
        Certification toUnlock = context.getObjectById(Certification.class, certification.getId());
        toUnlock.setLock(null);
        context.saveObject(toUnlock);
        context.commitTransaction();
    }
    
    /**
     * @param clazz Class to check the attribute name against
     * @param attributeName Proposed attribute name
     * @return true if the attribute name is already reserved as a property for the class in question; 
     *         false otherwise
     */
    public static boolean isReservedAttributeName(final SailPointContext context, final Class<? extends SailPointObject> clazz, final String attributeName) {
        // The absence of a name is likely unintended -- throw an exception
        if (attributeName == null) {
            throw new IllegalArgumentException("Missing an attribute name");
        }
        
        // Exempt extended attributes from validation
        if (ExtendedAttributeUtil.isExtendedAttribute(clazz, attributeName)) {
            return false;                                
        }
        
        HibernatePersistenceManager hpm = HibernatePersistenceManager.getHibernatePersistenceManager(context);
        Set<String> reservedNames = hpm.getProperties(clazz);
        return reservedNames.contains(attributeName.toLowerCase());
    }
    
    /**
     * Return whether a SailPointObject of the given type has a name.
     */
    public static boolean hasName(final SailPointContext context, final Class<? extends SailPointObject> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Missing a class to validate");
        }
        
        boolean hasName;
        try {
            // This is admittedly an ugly way to do this check but it's been in place for a long time
            // and there is no compelling reason to do otherwise
            SailPointObject obj = clazz.newInstance();
            hasName = obj.hasName();
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("The " + clazz.getName() + " class can't be instantiated because it lacks a default constructor", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("The " + clazz.getName() + " class can't be instantiated because its default constructor isn't public", e);
        }
        
        return hasName;
    }

    /**
     * Do a projection search across a set of IDs. This avoids SQL restrictions on the number
     * of parameters in an "IN" query, and returns as many iterators as necessary with results
     * @param context SailPointContext
     * @param clazz Item class to search
     * @param ids List if IDs to search across
     * @param otherFilters Any additional filters to add to ID filter
     * @param properties Any properties to return
     * @return SearchResultsIterator 
     * @throws GeneralException
     */
    public static SearchResultsIterator searchAcrossIds(SailPointContext context, Class clazz, List<String> ids,
                                                           List<Filter> otherFilters, List<String> properties)

            throws GeneralException {
        return searchAcrossIds(context, clazz, ids, otherFilters, properties, "id");
    }

    /**
     * Do a projection search across a set of IDs. This avoids SQL restrictions on the number
     * of parameters in an "IN" query, and returns as many iterators as necessary with results
     * @param context SailPointContext
     * @param clazz Item class to search
     * @param ids List if IDs to search across
     * @param otherFilters Any additional filters to add to ID filter
     * @param properties Any properties to return
     * @param idPropertyName Name of the properties that is used for ID filter
     * @return Iterator over iterators across results of search
     * @throws GeneralException
     */
    public static SearchResultsIterator searchAcrossIds(SailPointContext context, Class clazz, List<String> ids,
                                                           List<Filter> otherFilters, List<String> properties, String idPropertyName)

            throws GeneralException {

        return searchAcrossIds(context, clazz, ids, otherFilters, properties, idPropertyName, false);
    }

    /**
     * Do a projection search across a set of IDs. This avoids SQL restrictions on the number
     * of parameters in an "IN" query, and returns as many iterators as necessary with results
     * @param context SailPointContext
     * @param clazz Item class to search
     * @param ids List if IDs to search across
     * @param otherFilters Any additional filters to add to ID filter
     * @param properties Any properties to return
     * @param idPropertyName Name of the properties that is used for ID filter
     * @param isDistinct Sets distinct on the query options. Default is false.
     * @return Iterator over iterators across results of search
     * @throws GeneralException
     */
    public static SearchResultsIterator searchAcrossIds(SailPointContext context, Class clazz, List<String> ids,
                List<Filter> otherFilters, List<String> properties, String idPropertyName, boolean isDistinct)
            throws GeneralException {

        List<Iterator<Object[]>> iterators = new ArrayList<Iterator<Object[]>>();
        for (int iFrom = 0; iFrom < ids.size();) {
            int iTo = ((iFrom + MAX_IN_QUERY_SIZE) > ids.size()) ? ids.size() : iFrom + MAX_IN_QUERY_SIZE;
            List<String> subListIds = ids.subList(iFrom, iTo);
            QueryOptions ops = new QueryOptions();
            ops.setDistinct(isDistinct);
            ops.add(Filter.in(idPropertyName, subListIds));
            if (!Util.isEmpty(otherFilters)) {
                for (Filter f: otherFilters) {
                    ops.add(f);
                }
            }
            Iterator<Object[]> it = context.search(clazz, ops, properties);
            if (it != null) {
                iterators.add(it);
            }

            iFrom = iFrom + MAX_IN_QUERY_SIZE;
        }

        return new SearchResultsIterator(iterators.iterator());
    }
    
    /**
     * Cycle through an application's dependencies and make sure there are
     * no applications that reference each other to avoid infinite recursion
     * when processing things that use the dependency chain.
     * 
     * @param app
     * @throws GeneralException
     */
    public static void validateDependencies(Application app) throws GeneralException {        
        new DependencyValidator().validate(app);            
    }
    
    /**
     * Try to guess if a string looks encrypted.
     * This is intended only to help transition from storing
     * unencrypted strings, it is not 100% reliable but should
     * be close enough for our purposes.
     */
    @Untraced
    public static boolean isEncoded(String src) {
        boolean encrypted = false;
        if (src != null) {
            if (src.length() >= MINIMUM_ENCODED_LENGTH) {
                int delimiter = src.indexOf(ENCODING_DELIMITER);
                if (delimiter > 0 && delimiter < (src.length() - 1)) {
                    String id = src.substring(0, delimiter);
                    // Must contain only digits or one of the
                    // special algorithm names.  Util.atoi returns zero
                    // for non-numeric strings, there is no id zero
                    if (id.equals("ascii") || Util.atoi(id) > 0)
                        encrypted = true;
                }
            }
        }
        return encrypted;
    }
    
    /**
     * Before saving an identity, make sure sensitive attributes
     * are encrypted.
     * 
     * @ignore
     * This is a temporary kludge to make sure encryption
     * happens before we start doing it reliably in the UI layer.
     */
    public static void encryptIdentity(Identity user, SailPointContext context) {

        // don't let this throw in case something goes wrong
        try {
            String pass = user.getPassword();
            if (pass != null)
                user.setPassword(EncodingUtil.encode(pass, context));

            List<AuthenticationAnswer> answers = user.getAuthenticationAnswers();
            if (null != answers) {
                for (AuthenticationAnswer answer : answers) {
                    String a = answer.getAnswer();
                    if (null != a) {
                        //convert to lower case, since hashing does not support case insensitive match
                        if (!EncodingUtil.isEncrypted(a) && !EncodingUtil.isHashed(a)) {
                            a = a.toLowerCase();
                        }
                        answer.setAnswer(EncodingUtil.encode(a, context));
                    }
                }
            }
            
            //convert encrypted password history to hashed, if hashing is enabled
            if (EncodingUtil.isHashingEnabled(context)) {
                String pwHistory = user.getPasswordHistory();
                if (pwHistory != null) {
                    List<String> oldList = Util.csvToList(pwHistory);
                    List<String> newList = encryptPasswordHistory(oldList, context);
                    user.setPasswordHistory(Util.listToCsv(newList));
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
    }

    /**
     * Before saving a link, convert encrypted password history to hashed, 
     * if hashing is enabled.
     * 
     */
    public static void encryptLink(Link link, SailPointContext context) {
        // don't let this throw in case something goes wrong
        try {
            //convert encrypted password history to hashed, if hashing is enabled
            if (EncodingUtil.isHashingEnabled(context)) {
                String pwHistory = link.getPasswordHistory();
                if (pwHistory != null) {
                    List<String> oldList = Util.csvToList(pwHistory);
                    List<String> newList = encryptPasswordHistory(oldList, context);
                    link.setPasswordHistory(Util.listToCsv(newList));
                }                
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
    }
    
    @Untraced
    protected static List<String> encryptPasswordHistory(List<String> oldList, SailPointContext context)
            throws GeneralException {
        List<String> newList = new ArrayList<String>();
        for (String pw : Util.safeIterable(oldList)) {
            newList.add(EncodingUtil.encode(pw, context));
        }
        return newList;
    }

    /**
     * This will find the Workflow name based on the flow.
     * What it does is prepends 'workflowLCM' to the flow name
     * and tries to lookup a system configuration value set with that name
     * 
     * For example, if flowName is 'ResetPassword' it will lookup
     * the system config property 'workflowLCMResetPassword' and that value
     * will be returned. 
     */
    public static String getLCMWorkflowName(SailPointContext ctx, String flowName) 
            throws GeneralException {

        String workflow = null;
        Configuration sysConfig = ctx.getConfiguration();
        if ( sysConfig != null ) {
            String configName = "workflowLCM"+ flowName;
            String configuredWorkflow = sysConfig.getString(configName);
            if ( Util.getString(configuredWorkflow) != null ) {
                workflow = configuredWorkflow;
            } else {
                throw new GeneralException("Unable to find system config system settting for flow '"+flowName+"' using config name'"+configName+"'");
            }
        }
        return workflow;
    }

    /**
     * Simple private class that keeps track of the dependency chain
     * to avoid infinite recursion.
     * 
     * @author Dan.Smith
     *
     */
    private static class DependencyValidator {
        
        Set<String> allApps = new HashSet<String>();
        
        Application targetApp = null;
        
        public DependencyValidator() {
            allApps = new HashSet<String>();
        }

        /**
         * Go through the target application and make sure nothing else
         * References the target.0$a
         * 
         * @param app
         * @throws GeneralException
         */
        public void validate(Application app) throws GeneralException {            
            if ( app != null ) {
                if ( targetApp == null ) {
                    targetApp = app;

                }
                List<Application> deps = app.getDependencies();
                if ( deps != null ) {
                    for (Application dep : deps) {
                        String nameOrId = dep.getName();
                        if ( nameOrId == null ) 
                            nameOrId = dep.getId();
                        
                        if ( Util.nullSafeCompareTo(getSourceIdentitifer(),  nameOrId) == 0  )  {
                            throw new GeneralException("Dependency Application ["+dep.getName()+"] was already found as a dependency in the dependency chain for ["+ getSourceIdentitifer() +"].");    
                        } else {
                            allApps.add(nameOrId);
                        }     
                        validate(dep);
                    }
                }
            }
        }
                
        private String getSourceIdentitifer() {
            String sourceName = null;
            if ( targetApp != null ) {
                sourceName = targetApp.getName();
                if ( sourceName == null ) {
                    sourceName = targetApp.getId();
                }
            }
            return sourceName;
        }
             
        
    }

    /**
     * Returns the application associated with this Connector depending
     * upon whether its a cloud based application or not
     * @param conn  Connector object whose application object is
     *              to be retrieved
     *
     * @ignore
     * jsl - this should be a method on AbstractConnector
     */
    public static Application getLocalApplication(Connector conn) {
        Application app = null;
        if(conn != null) {
            app = conn.getTargetApplication();
            if(app == null) {
                app = conn.getApplication();
            }
        }
        return app;
    }

    /**
     * Updates application config with new values if any
     * @param spContext  The sailpointcontext object
     * @param app The application that has modified values
     * @throws GeneralException
     */
    public static void updateApplicationConfig(SailPointContext spContext, Application app)
        throws GeneralException {
        updateApplicationConfig(spContext, app, false);
    }
    
     /**
     * Updates application config with new values if any
     * @param spContext  The sailpointcontext object
     * @param app The application that has modified values
     * @param isCG  Flag to check if it is a IDN/CIB application
     * @throws GeneralException
     */
    public static void updateApplicationConfig(SailPointContext spContext, Application app, boolean isCG)
        throws GeneralException {
        // _application is not stable, have to fetch and lock
        // Don't terminate the task if this fails?
        // !! Ordinarilly I would use a persistent lock here
        // but Application doesn't have a lock column and I don't
        // want to mess with an database upgrade.  Just use a 
        // transactionlock.  Note though that this will guard against
        // parallel aggs on the same App (unlikely) but it won't guard
        // against an app being edited since we have not been in the
        // habbit of locking apps in the editor.
        if (app != null && Util.isNotNullOrEmpty(app.getId())) {
            Map<String, Object> connState = (Map<String, Object>) app
                    .getAttributeValue(Application.CONNECTOR_STATE_MAP);
            if (!Util.isEmpty(connState)) {
                String lockMode = PersistenceManager.LOCK_TYPE_TRANSACTION;
                Application appdB = ObjectUtil.lockObject(spContext,
                        Application.class, null, app.getName(), lockMode);
                
                //For CIB/IDN provisioning use cases - connector state map values
                //are not updated on the IIQ/CIS (database) side. Hence connector
                //state map should be kept as it is to be synced back to the database
                if (!isCG) {
                    // connectorstate map should be removed fist before saving it to
                    // application else this call will go inside infinite loop in case
                    // of aggregation
                    app.removeAttribute(Application.CONNECTOR_STATE_MAP);
                }

                connState.keySet().forEach(key -> {
                    appdB.setAttribute(key, connState.get(key));
                    // to synch local object copy with persisted one
                    app.setAttribute(key, connState.get(key));
                });

                try {
                    spContext.saveObject(appdB);
                } finally {
                    // this will commit
                    ObjectUtil.unlockObject(spContext, appdB, lockMode);
                }
            }
        }
    }
    
    /**
     * It will update non-persisted application config with new values if any
     * in connectorstatemap
     * @param app  Application that has modified values and
     * not persisted yet
     * @throws GeneralException
     */
    public static void updateUnSavedApplicationConfig(Application app)
            throws GeneralException {
        if (Util.isNullOrEmpty(app.getId())) {
            Map<String, Object> connState = (Map<String, Object>) app
                    .getAttributeValue(Application.CONNECTOR_STATE_MAP);
            // connectorstate map should be removed fist before saving it to
            // application else this call will go inside infinite loop
            app.removeAttribute(Application.CONNECTOR_STATE_MAP);
            if (!Util.isEmpty(connState)) {
                connState.keySet().forEach(key -> {
                    app.setAttribute(key, connState.get(key));
                });
            }
        }
    }
    /**
     * Convert a list of objects to a list of maps of values matching the list provided
     * @param objects Objects to convert
     * @param properties Properties to pull from the objects and put in the maps
     * @return List of maps containing values from each object
     * @throws GeneralException
     */
    public static List<Map<String, Object>> objectsToMap(List<? extends Object> objects, List<String> properties) throws GeneralException {
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
        for (Object object: Util.safeIterable(objects)) {
            maps.add(objectToMap(object, properties));
        }
        
        return maps;
    }
    
    /**
     * Convert an object to a map of values matching the list provided.
     * @param object Object to convert
     * @param properties Properties to pull from the object and put in the map
     * @return Map containing values from the object
     * @throws GeneralException
     */
    public static Map<String, Object> objectToMap(Object object, List<String> properties)
            throws GeneralException{
        Map<String,Object> map = new HashMap<String,Object>();
        for (String property : properties) {
            Object value = objectToPropertyValue(object, property);
            map.put(property, value);
        }
        return map;
    }

    /**
     * Attempt to get a value for the given property from the given object.  If not a property on the object,
     * extended attributes will be examined as well. 
     */
    public static Object objectToPropertyValue(Object object, String property) throws GeneralException {
        Object value = null;
        try {
            value = PropertyUtils.getNestedProperty(object, property);
        }
        catch (NestedNullException nne) {
            // This gets thrown if a dotted property has a null along the way.
            // Just return a null.
        }
        catch (NoSuchMethodException nme) {
            // This gets thrown if it's an extended attribute and doesn't
            // have an accessor method.  The class is expected to 
            // implement getExtendedAttributes and return the attributes map
            if (object instanceof SailPointObject) {
                SailPointObject spo = (SailPointObject)object;
                Map<String,Object> atts = spo.getExtendedAttributes();
                if (atts != null)
                    value = atts.get(property);
            }
        }
        catch (Exception e) {
            throw new GeneralException(e);
        }
        return value;
    }

    /**
     * Delete a SailpointObject using sailpoint context
     * @param context The Sailpoint context 
     * @param spObj The Sailpoint object to be deleted
     * @throws GeneralException
     */
    public static void deleteObject(SailPointContext context, SailPointObject spObj)
        throws GeneralException {
        // fetch a fresh one so it can be in the session
        // so Terminator can follow links
        spObj = context.getObjectById(spObj.getClass(), spObj.getId());
        if (spObj != null) {
            Terminator t = new Terminator(context);
            t.deleteObject(spObj);
        }
    }

    //////////////////////////////////////////////////////////////////////.
    //
    // Target Permissions
    //
    //////////////////////////////////////////////////////////////////////.

    /**
     * Return the list of target permissions associated with an account.
     * Formerly the list was stored directly in the Link attributes map, 
     * in 7.2 that was removed and we have to query.
     *
     * @see #getTargetPermissions(SailPointContext, Link, String)
     */
    public static List<Permission> getTargetPermissions(SailPointContext context, Link link)
        throws GeneralException {

        return getTargetPermissions(context, link, null, null);
    }

    public static List<Permission> getTargetPermissions(SailPointContext context, Link link, String target) throws GeneralException {
        return getTargetPermissions(context, link, target, null);
    }

    /**
     * Return the list of target permissions associated with an account,
     * optionally only returning permissions that are related to a given
     * target.
     *
     * Formerly the list was stored directly in the Link attributes map, 
     * in 7.2 that was removed and we have to query.
     *
     * @ignore
     * There are two ways this could be built, querying on IdentityEntitlement
     * and querying on TargetAssociation.  TargetAssociation is the model
     * TargetCollector builds and we promote those to IdentityEntitlements.
     * They're both about the same, the main difference is that TargetAssociation
     * has a CSV of rights, and IdentityEntitlements are broken out one for each 
     * right.  
     *
     * TargetAssociation is a little easier to query on since we have the Link
     * id rather than having to use a combination of identity, application, and
     * instance.  This might be faster, I'm using IdenityEntitlement because
     * this can serve as a better example for querying all types of entitlements.
     */
    public static List<Permission> getTargetPermissions(SailPointContext context, Link link, String target, Filter targetAssociationFilter)
        throws GeneralException {

        List<Permission> perms = new ArrayList<Permission>();

        // try both to see which is faster
        // NOTE: IF this changes to use identity entitlement table, the passed in filter no longer works. DONT DO IT!
        boolean useIdentityEntitlements = false;

        if (useIdentityEntitlements) {

            QueryOptions ops = new QueryOptions();
            // have to have all four of these to use the composite index
            ops.add(Filter.eq("identity", link.getIdentity()));
            ops.add(Filter.eq("application", link.getApplication()));
            ops.add(Filter.eq("instance", link.getInstance()));
            ops.add(Filter.eq("nativeIdentity", link.getNativeIdentity()));
            if (null != target) {
                ops.add(Filter.eq("name", target));
            }
        
            // having a type TargetPermission would avoid having to include source
            // which is not indexed, don't need to include type as long
            // as TargetAggregation can only create target permissions
            // ops.add(Filter.eq("type", ManagedAttribute.Type.Permission));
            ops.add(Filter.eq("source", Source.TargetAggregation));

            // todo: have the short/long target name issue here
            List<String> props = new ArrayList<String>();
            props.add("name");
            props.add("value");
            
            // TODO: should we collapse the rights to a csv?
            // That's what the old target Permissions inside the EntitlementGroup looked like
            Iterator<Object[]> it = context.search(IdentityEntitlement.class, ops, props);
            while (it.hasNext()) {
                Object[] row = it.next();
                Permission perm = new Permission();
                perm.setTarget((String)(row[0]));
                perm.setRights((String)(row[1]));
                perms.add(perm);
            }
        }
        else {
            QueryOptions ops = getTargetAssociationQueryOptions(link, target);
            if (targetAssociationFilter != null) {
                ops.add(targetAssociationFilter);
            }
            
            List<String> props = new ArrayList<String>();
            props.add("rights");
            props.add("target.name");
            props.add("target.fullPath");
            props.add("target.targetHost");
            // should we copy this to the TargetAssociation too?
            props.add("target.targetSource.name");
            props.add("target.application.name");

            Iterator<Object[]> it = context.search(TargetAssociation.class, ops, props);
            while (it.hasNext()) {
                Object[] row = it.next();
                String rights = (String) row[0];
                String targetName = (String) row[1];
                String targetFullPath = (String) row[2];
                String targetHost = (String) row[3];
                String targetSource = (String) row[4];
                String appName = (String) row[5];

                // The aggregation source can either a TargetSource or an Application.
                String aggSource = (null != targetSource) ? targetSource : appName;

                Permission perm =
                    TargetAggregator.createTargetPermission(rights, targetName, targetFullPath, targetHost, aggSource);

                // The QueryOptions lookup the TargetAssociations using a hash - which may actually return some
                // incorrect targets (very unlikely).  Be safe and make sure we only add targets that match.
                if ((null == target) || target.equals(perm.getTarget())) {
                    perms.add(perm);
                }
            }
        }

        return perms;
    }

    public static boolean hasTargetPermissions(SailPointContext context, Link link, String target)
        throws GeneralException {

        QueryOptions ops = getTargetAssociationQueryOptions(link, target);

        return context.countObjects(TargetAssociation.class, ops) > 0;
    }

    /**
     * @return true if the given CorrelationConfig has a single attribute assignment
     */
    public static boolean isSimple(CorrelationConfig corrConfig ) {
        boolean isSimple = false;
        if (corrConfig != null) {
            List<Filter> attrAssignments = corrConfig.getAttributeAssignments();
            List<DirectAssignment> directAssignments = corrConfig.getDirectAssignments();

            isSimple = Util.isEmpty(directAssignments) &&
                    (!Util.isEmpty(attrAssignments) && attrAssignments.size() == 1);
        }
        return isSimple;
    }

    /**
     * Return the QueryOptions that will find target permission TargetAssociations for the given link
     * on the given target.  Note that if a target is specified, this uses the unique name hash to look
     * up the TargetAssociations.  The hash is not guaranteed to be unique for different values of unique
     * names, so the calling code needs to ensure that the unique name of the returned TargetAssociations
     * matches the given target.
     *
     * @param link  The Link for which to find TargetAssociations.
     * @param target  The name or unique name of the target for the target permissions.  If null,
     *     TargetAssociations for all targets for the link will be returned.
     *
     * @return QueryOptions that will find target permission TargetAssociations for the given link
     *      on the given target.
     */
    private static QueryOptions getTargetAssociationQueryOptions(Link link, String target) {
        QueryOptions ops = new QueryOptions();
        // have to have all four of these to use the composite index
        ops.add(Filter.eq("objectId", link.getId()));
        // this is not indexed, explore...
        ops.add(Filter.eq("targetType", TargetAssociation.TargetType.TP.name()));

        if (null != target) {
            // We need to match on the unique key for the target - which may be the hash of the name or fullPath.
            String uniqueHash = Target.createUniqueHash(target);
            ops.add(Filter.eq("target.uniqueNameHash", uniqueHash));
        }

        return ops;
    }

    /**
     * Return a single Permission that has all of the rights for the requested target permission that
     * the given account has.
     *
     * @param context  The SailPointContext.
     * @param link  The Link for which to return the target permission.
     * @param target  The name or full path of the target for which to return the permissions.
     *
     * @return A single Permission that has all of the rights for the requested target permission that
     *      the given account has, or null if the account has no target permissions for the requested
     *      target.
     */
    public static Permission getTargetPermission(SailPointContext context, Link link, String target)
        throws GeneralException {

        Permission combinedPerm = null;

        // We may get multiple permissions that all relate to the same target.  If so, we'll combine them.
        List<Permission> perms = getTargetPermissions(context, link, target);
        for (Permission perm : Util.iterate(perms)) {
            // First time through - create a Permission on which to store all of the rights.
            if (null == combinedPerm) {
                combinedPerm = (Permission) perm.clone();
                combinedPerm.setRights((List<String>) null);
            }

            // This should be non-null, but we'll be extra safe.
            if (null != perm.getRightsList()) {
                combinedPerm.addRights(perm.getRightsList());
            }
        }

        return combinedPerm;
    }

    /**
     * Given a Permission that has all rights for a given target permission, set the target permissions
     * for the requested identity.  Any existing target permission right that is not found in the given
     * permission will be removed from the account.  All other existing target permissions will remain
     * on the account.
     *
     * @param context  The SailPointContext.
     * @param link  The Link for which to set the target permission.
     * @param perm  The target permission to set for the given account.
     */
    public static void setTargetPermission(SailPointContext context, Link link, Permission perm)
        throws GeneralException {

        List<String> newRights = perm.getRightsList();
        List<TargetAssociation> toDelete = new ArrayList<>();
        List<String> existingRights = new ArrayList<>();

        // Get all of the existing TargetAssociations for the Permission that is being set.
        QueryOptions qo = getTargetAssociationQueryOptions(link, perm.getTarget());
        List<TargetAssociation> existingAssociations = context.getObjects(TargetAssociation.class, qo);

        // Iterate over the existing TargetAssociations, removing any rights that need to go away and keeping
        // track of which rights will need to be added.
        // Note: considered decaching in this loop, but the TargetAggregator does not when it is building this
        // model ... so we won't worry about it either for now.
        for (TargetAssociation ta : existingAssociations) {
            // The QueryOptions above search using a hash - which may actually return Targets that don't match
            // the Permission's target (very unlikely).  Make sure these match before trying to remove rights.
            if (perm.getTarget().equals(ta.getTarget().getUniqueName()) && (null != ta.getRights())) {
                List<String> taRights = Util.csvToList(ta.getRights());

                // First, remove any rights on the TargetAssociation that are not being set by the permission.
                taRights.retainAll(newRights);

                // If the TargetAssociation has no rights left, we'll delete it.
                if (taRights.isEmpty()) {
                    toDelete.add(ta);
                }
                else {
                    // Otherwise, keep track of which rights are already in the DB, so we'll know which to add.
                    existingRights.addAll(taRights);
                    ta.setRights(Util.listToCsv(taRights));
                    context.saveObject(ta);
                }
            }
        }

        // If there are any rights that aren't saved in the database, create new TargetAssociations for them.
        List<String> toAdd = new ArrayList<>(newRights);
        toAdd.removeAll(existingRights);
        createTargetPermissionAssociations(context, link, perm, toAdd);

        // If there are any TargetAssociations that are empty, delete them.
        if (!toDelete.isEmpty()) {
            Terminator t = new Terminator(context);
            for (TargetAssociation deleteMe : toDelete) {
                t.deleteObject(deleteMe);
            }
        }

        // Commit everything.
        context.commitTransaction();
    }

    /**
     * Create and persist target permission TargetAssocations for each of the given rights.
     *
     * @param context  The SailPointContext to use.
     * @param link  The Link to which to add the target permissions.
     * @param permission  The Permission that has the name of the target and the aggregation source.
     * @param rights  A possibly null or empty list of rights for which to create TargetAssociations.
     */
    private static void createTargetPermissionAssociations(SailPointContext context, Link link,
                                                           Permission permission, List<String> rights)
        throws GeneralException {

        if (!Util.isEmpty(rights)) {
            Target target = getTarget(context, link, permission.getTarget(), permission.getAggregationSource());

            for (String right : rights) {
                TargetAssociation ta = new TargetAssociation();
                ta.setObjectId(link.getId());
                ta.setOwnerType(TargetAssociation.OwnerType.L.name());
                ta.setRights(right);
                ta.setTarget(target);
                ta.setTargetName(target.getDisplayableName());
                ta.setTargetType(TargetAssociation.TargetType.TP.name());
                context.saveObject(ta);
            }
        }
    }

    /**
     * Return the target for the given application with the given name.
     *
     * @param context  The SailPointContext.
     * @param link  The Link that has the application for which to find the target.
     * @param targetName  The name of the target.
     * @param aggSource  The name of the aggregation source - either the Application or TargetSource for the Target.
     *
     * @return The target for the given application with the given name.
     *
     * @throws GeneralException  If a single Target cannot be found for the given application and target name.
     */
    private static Target getTarget(SailPointContext context, Link link, String targetName, String aggSource)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();

        // We need to match on the unique key for the target - which may be the hash of the name or fullPath.
        String uniqueHash = Target.createUniqueHash(targetName);
        qo.add(Filter.eq("uniqueNameHash", uniqueHash));

        // Some Targets can come directly from applications (for connectors that support target aggregation).
        Filter appFilters = Filter.eq("application", link.getApplication());

        // If there are target sources for the application, also return Targets for these sources.
        List<String> targetSourceIds = getTargetSourceIdsWithName(link, aggSource);
        if (!targetSourceIds.isEmpty()) {
            Filter tsFilter = Filter.in("targetSource.id", targetSourceIds);
            appFilters = Filter.or(appFilters, tsFilter);
        }

        qo.add(appFilters);

        // Query away!
        List<Target> targets = context.getObjects(Target.class, qo);

        // We should find exactly one target ... if not then we'll throw.
        if (1 != targets.size()) {
            throw new GeneralException("Expected to find a single target for " + targetName + " on " + link.getApplicationName());
        }

        return targets.get(0);
    }

    /**
     * Return a List of the IDs of the TargetSources for the application referenced by the given link that have the
     * given name, or an empty list if there are none.
     */
    private static List<String> getTargetSourceIdsWithName(Link link, String targetSourceName) {
        List<String> ids = new ArrayList<>();

        for (TargetSource ts : link.getApplication().getTargetSources()) {
            if (Util.nullSafeEq(targetSourceName, ts.getName())) {
                ids.add(ts.getId());
            }
        }

        return ids;
    }

    /**
     * Convenience helper to extract a single String property from an object in the database.
     *
     * @param filter Filter to use for the projection query. Use a filter that will return a
     * unique result such as passing in a name or id filter.
     * @param returnProperty The property on the object to retrieve
     * @param context The SailPointContext
     * @param clazz The Class of the Object
     * @return The String value of the property on the object.
     * @throws GeneralException
     */
    public static <T extends SailPointObject> String getStringPropertyFromObject(Filter filter,
            String returnProperty, SailPointContext context, Class<T> clazz) throws GeneralException {
        String property = null;

        if (filter != null && context != null && clazz != null && Util.isNotNullOrEmpty(returnProperty)) {
            QueryOptions options = new QueryOptions(filter);
            Iterator<Object[]> results = context.search(clazz, options, returnProperty);

            // Use if logic since there should only be one row returned
            if (results != null && results.hasNext()) {
                Object[] row = results.next();

                // We only expect one row returned. But if we get more than 1, flush the iterator. 
                if (results.hasNext()) {
                    Util.flushIterator(results);

                    if (log.isErrorEnabled()) {
                        log.error("The Filter used in the projection query did not return a unique result.");
                    }

                }

                // should only return 1 column due to the .search() parameters
                if (row.length == 1) {
                    property = (String) row[0];
                }
            }
        }

        return property;
    }
    
    /**
     * Looks at the attribute defs within an object config to check if the attribute is a valid extended
     * attribute af a class and then returns the class.
     * 
     * @param clazz Class of sailpoint object that ideally has an object config
     * @param attributeName Extended attribute name
     */
    public static Class getExtendedAttributeClass(Class clazz, String attributeName){
        Class classType = null;
        ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
        
        if (conf != null) {
            ObjectAttribute oa = conf.getObjectAttribute(attributeName);
            if (oa != null) {
                if (oa.getPropertyType() != null) {
                     classType = oa.getPropertyType().getTypeClazz();
                }
            }
        }
        
        return classType;
    }

    /**
     * There are limits imposed by database drivers regarding the number of values that can be included in
     * an "IN" query condition. This method will split values to be used in the "IN" condition into multiple
     * filters which can be processed separately. Uses a default value for the partition size.
     *
     * @param propertyName - name of database property to use in the filter
     * @param values - list of values that will be included in the "IN" condition
     * @return List<Filter> - list of "IN" condition filters
     */
    public static List<Filter> getPartitionedInFilters(String propertyName, List<String> values) {
        return getPartitionedInFilters(propertyName, values, MAX_IN_QUERY_SIZE);
    }

    /**
     * There are limits imposed by database drivers regarding the number of values that can be included in
     * an "IN" query condition. This method will split values to be used in the "IN" condition into multiple
     * filters which can be processed separately.
     *
     * @param propertyName - name of database property to use in the filter
     * @param values - list of values that will be included in the "IN" condition
     * @param partitionSize - max number of values to include in the "IN" statement
     * @return List<Filter> - list of "IN" condition filters
     */
    public static List<Filter> getPartitionedInFilters(String propertyName, List<String> values, int partitionSize) {
        List<Filter> filters = new ArrayList<>();

        if ((values != null) && !values.isEmpty() && StringUtils.isNotEmpty(propertyName)) {
            for (List<String> list : ListUtils.partition(values, (partitionSize <= 0 ? MAX_IN_QUERY_SIZE : partitionSize))) {
                filters.add(Filter.in(propertyName, list));
            }
        }

        return filters;
    }
}
