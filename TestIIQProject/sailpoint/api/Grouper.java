/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating operations related to generating
 * group definitions from group factories.  
 *
 * This also provides an interface to perform group index
 * generation, which we pass along to ScoreKeeper to do most
 * of the work.
 *
 * Meter Range: 80-99
 * 
 * Author: Jeff
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.GroupIndex;
import sailpoint.object.Identity;
import sailpoint.object.IdentityExternalAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.ScoreConfig;
import sailpoint.object.TaskResult;
import sailpoint.search.ExternalAttributeFilterBuilder;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
// !! this is only for some ARG constants, need to move
// these and share

public class Grouper {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Arguments from the caller
    //

    public static final String ARG_SCORE_CONFIG = "scoreConfig";
    
    public static final String ARG_DELETE_DORMANT_GROUPS = "deleteDormantGroups";

    //
    // Return values
    //

    public static final String RET_CREATED = "groupsCreated";
    public static final String RET_RETAINED = "groupsRetained";
    public static final String RET_DORMANT = "groupsDormant";
    public static final String RET_DELETED = "groupsDeleted";
    public static final String RET_INDEXED = "groupsIndexed";
    public static final String RET_INDEXES_DELETED = "groupIndexesDeleted";

    public static final String RET_FACTORY_NAMES = "factoryNames";
    public static final String RET_GROUP_NAMES = "groupNames";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(Grouper.class);

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    private SailPointContext _context;

    /**
     * Object we can use to report progress.
     */
    TaskMonitor _monitor;

    /**
     * Optional map of values that may be used to customize behavior.
     * Normally this will be the TaskExecutor argument map.
     */
    private Attributes<String,Object> _arguments;

    /**
     * Enable trace messages for the bulk refresh methods.
     */
    boolean _trace;

    /**
     * Option to delete dormant groups as we find them.
     * Not currently exposed.
     */
    boolean _deleteDormantGroups;

    //
    // Runtime state
    //

    /**
     * Cached system configuration.
     */
    Configuration _systemConfig;

    /**
     * Cached score configuration.
     */
    ScoreConfig _scoreConfig;

    /**
     * Termination flag that will halt group processing.
     */
    boolean _terminate;

    /**
     * Internal score keeper for calculating group scores and
     * managing GroupIndex histories.
     */
    ScoreKeeper _scoreKeeper;
    
    /**
     * BatchCommmitter used for processing large numbers of GroupDefinitions
     * without bloating the Hibernate cache
     */
    BatchCommitter<GroupDefinition> _committer;

    /**
     * Random number generator for generateGroupHistory().
     */
    Random _random;

    //
    // Statistics
    //

    int _groupsCreated;
    int _groupsRetained;
    int _groupsDormant;
    int _groupsDeleted;
    int _groupsIndexed;
    int _groupIndexesDeleted;

    /**
     * The names of the GroupFactory objects we refreshed.
     */
    List<String> _factoryNames;

    /**
     * The names of the standalone GroupDefinitions we refreshed.
     */
    List<String> _groupNames;

    BasicMessageRepository _messages;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Grouper(SailPointContext context) {
        _context = context;
        _messages = new BasicMessageRepository();
        _committer = new BatchCommitter<GroupDefinition>(GroupDefinition.class, _context);
    }

    public Grouper(SailPointContext context, Attributes<String,Object> args) {
        this(context);
        _arguments = args;
    }

    public Grouper(SailPointContext context,
                   TaskMonitor monitor,
                   Attributes<String,Object> args) {
        this(context, args);
        _monitor = monitor;
        
        _deleteDormantGroups = args.getBoolean(ARG_DELETE_DORMANT_GROUPS, _deleteDormantGroups);
    }

    public List<Message> getMessages() {
        return _messages != null ? _messages.getMessages() : new ArrayList<Message>();
    }

    public void setTerminate(boolean b) {
        _terminate = b;
        if (_scoreKeeper != null)
            _scoreKeeper.setTerminate(b);
        
        if (_committer != null) {
        	_committer.setTerminate(b);
        }
    }

    public void setTrace(boolean b) {
        _trace = b;
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    private void trace(String msg) {
        log.info(msg);
        
        if (_trace)
            println(msg);
    }

    /**
     * When used by the IdentityRefreshExecutor, causes our
     * accumulated statistics to be logged.
     */
    public void traceStatistics() {

        trace(Util.itoa(_groupsCreated) + " factory groups created.");
        trace(Util.itoa(_groupsRetained) + " factory groups retained.");
        trace(Util.itoa(_groupsDormant) + " factory groups dormant.");
        trace(Util.itoa(_groupsDeleted) + " factory groups deleted.");
        trace(Util.itoa(_groupsIndexed) + " groups indexed.");
        trace(Util.itoa(_groupIndexesDeleted) + " group indexes replaced.");

        if (_factoryNames != null && _factoryNames.size() > 0)
            trace("Group factories processed: " + Util.listToCsv(_factoryNames));

        if (_groupNames != null && _groupNames.size() > 0)
            trace("Standalone groups processed: " + Util.listToCsv(_groupNames));
    }

    /**
     * When used by the IdentityRefreshExecutor, copy our relevant
     * statistics into a task result.
     */
    public void saveResults(TaskResult result) {

        result.setAttribute(RET_CREATED, Util.itoa(_groupsCreated));
        result.setAttribute(RET_RETAINED, Util.itoa(_groupsRetained));
        result.setAttribute(RET_DORMANT, Util.itoa(_groupsDormant));
        result.setAttribute(RET_DELETED, Util.itoa(_groupsDeleted));
        result.setAttribute(RET_INDEXED, Util.itoa(_groupsIndexed));
        result.setAttribute(RET_INDEXES_DELETED, Util.itoa(_groupIndexesDeleted));

        if (_factoryNames != null && _factoryNames.size() > 0)
            result.setAttribute(RET_FACTORY_NAMES, Util.listToCsv(_factoryNames));

        if (_groupNames != null && _groupNames.size() > 0)
            result.setAttribute(RET_GROUP_NAMES, Util.listToCsv(_groupNames));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Prepare
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Preload persistent state to the extent possible and cache it.
     */
    public void prepare() throws GeneralException {

        if (_arguments != null) {
            if (!_trace)
                _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);
        }

        _scoreKeeper = new ScoreKeeper(_context, _monitor, _arguments);
        _scoreKeeper.prepare();
    }

    public ScoreKeeper getScoreKeeper() throws GeneralException {
        if (_scoreKeeper == null)
            prepare();
        return _scoreKeeper;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // GroupFactory Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process the group factories.
     *
     * Here we look at the GroupFactory objects, and manage the collection
     * of their associated GroupDefinition objects.  We do not process
     * any GroupDefinition actions such as indexing.  Indexing is handled
     * in in a different method because we may need to index standalone
     * GroupDefinitions that were not generated from a factory.
     * 
     * For each active factory, we perform a "distinct" query to determine
     * the unique set of values for the factory attribute.  Next we mark
     * all of the current GroupDefinitions associated with this factory
     * as "unprocessed".  Next we iterate over the group names creating
     * new GroupDefinitions if they do not exist, or marking existing
     * definitiones as "processed".  
     *
     * At the end, any GroupDefinition associated with a factory that
     * is still marked "unprocessed" has no members.  It could be deleted,
     * but if there is scoring history for this group we may want to keep it
     * in case the group starts having members again.  If we do that, then 
     * we'll need a way to delete GroupDefinitions and their history if
     * it is no longer valid.
     *
     */
    public void refreshFactories() throws GeneralException {

        trace("Refreshing all group factories");
        Meter.enter(80, "refreshFactories");

        if (_context == null)
            throw new GeneralException("Missing SailPointContext");

        _groupsCreated = 0;
        _groupsRetained = 0;
        _groupsDormant = 0;
        _groupsDeleted = 0;

        // keep a list of the factory names we refresh to return    
        // in the task result
        _factoryNames = new ArrayList<String>();

        // Note that we're not trying to detect unreferenced 
        // GroupDefinitions if the GroupFactory is not enabled.
        // This may be another source of garbage, we could assume that
        // once a GroupFactory is disabled that we're supposed to clean
        // up all the related objects?

        // TODO: Since we're fetching the factory objects 
        // we can't 
        List<GroupFactory> factories = 
            _context.getObjects(GroupFactory.class, null);

        if (factories != null) {
            Iterator<GroupFactory> it = factories.iterator();

            // this is a potentially long running operation so monitor
            // a termination flag
            while (it.hasNext() && !_terminate) {
                GroupFactory f = it.next();
                if (f.isEnabled()) {
                    _factoryNames.add(f.getName());
                    refreshFactory(f);
                    assignGroupOwners(f);
                }
            }
        }

        Meter.exit(80);
        trace("Finished refreshing all group factories");
    }

    /**
     * Regenerate one group factory.
     *
     * The scalable way to detect dormant groups is to mark
     * them in the database, then unmark then as we process
     * the factory result.  But this is kind of annoying and
     * causes a lot of Hibernate overhead.  For now, assume
     * that we will have a mangeable number of group definitions
     * for each factory (a few thousand) and can do the "marking"
     * with an in-memory dictionary.
     *
     */
    public void refreshFactory(GroupFactory factory) throws GeneralException {

        Meter.enter(81, "refreshFactory");

        // Find the current groups for this factory.
        // Don't fetch the entire group, we just need the names.
        Map<String,String> groups = getCurrentGroups(factory);

        String attname = factory.getFactoryAttribute();

        if (attname == null) {
            // Hack, we allow this to represent the "Global" group
            // type that includes all identities.
            // Hmm, not sure this is such a good idea, it will be
            // expensive to index and its an easy mistake.
            //log.error("GroupFactory with undefined factory attribute!");

            refreshFactoryGlobal(factory, groups);
        }
        else if (attname.equals(Identity.ATT_BUNDLES)) {
            // complex multi-valued references 
            refreshFactoryBundles(factory, groups);
        }
        else if (attname.equals(Identity.ATT_MANAGER)) {
            // Originally did this as a simple attribute but
            // it resulted in Identity objects being serialized
            // inside the Filter saved in the GroupDefinition.
            // Catch it as a special case and convert it to 
            // a manager.name search.  An alternative would
            // be to make Fitler smarter and use <Reference> but
            // there shouldn't be many of these.
            refreshFactoryManager(factory, groups);
        }
        else {
            // assume it is a simple atomic value searchable attribute
            trace("Generating groups for factory " + factory.getName());
            
            ObjectConfig config = Identity.getObjectConfig();
            ObjectAttribute def = config.getObjectAttribute(attname);
            
            boolean multi = false;
            if (def != null)
                multi = def.isMulti();
            else {
                // I guess go on and hope for the best?
                if (log.isErrorEnabled())
                    log.error("Group factory attribute not configured: " + attname);
            }
            
            try {
                QueryOptions ops = new QueryOptions();
                ops.setDistinct(true);
                ops.setCloneResults(true);
                List<String> props = new ArrayList<String>();
                Iterator<Object[]> it;

                if (!multi) {
                    if (isMultiSystemAttribute(attname)) {
                        props.add(attname + ".name");
                    } else {
                        // simple attribute
                        props.add(attname);
                    }

                    it = _context.search(Identity.class, ops, props);
                }
                else {
                    // As of 3.2 multi-valued externals are stored in 
                    // a special table.  Don't particularly like having
                    // to special case this but trying to bury the
                    // the transformation under the filter compiler is hard.
                    // ExternalAttributeFilterBulider would be a good place for this?
                    ops.add(Filter.ignoreCase(Filter.eq("attributeName", attname)));
                    props.add("value");
                    it = _context.search(IdentityExternalAttribute.class, ops, props);
                }

                if (!it.hasNext()) {
                    _messages.addMessage(new Message(Message.Type.Warn,
                                                     MessageKeys.ERR_MISSING_GRP_ATTR, factory.getName(), attname));
                }
                
                while (it.hasNext() && !_terminate) {                
                    Object obj = (it.next()[0]);
                    String value = null;

                    // jsl - I guess we have to do this for the manager attribute
                    if (obj instanceof Identity) {
                        value = ((Identity)obj).getName();
                    } else if (obj != null) {                    
                        value = obj.toString();
                    }

                    String groupName = value;
                    if (value == null) {
                        // One or more Identities had a null value for
                        // the factory attribute.
                        // Generate a non-null name for diagnostics, but the
                        // UI should be displaying something nicer.
                        // ?? Might want a way to mark the GroupDefinition as
                        // representing the null value, so we don't rely on this
                        // naming convention.  I guess we can do that by looking
                        // at the filter.
                        groupName = "No " + factory.getName();
                    }
    
                    trace("Checking group definition: " + groupName);

                    // locate the existing definition, do these one at a time
                    // so we don't overload the cache
    
                    GroupDefinition group = getGroup(factory, groupName);
    
                    if (group != null) {
                        
                        // hmm, since we've got it should we have an option 
                        // to go ahead and refresh it now?  If we were doing
                        // orphan checking by marking the object, here is where
                        // we would clear the mark.  
    
                        if (groups != null)
                            groups.remove(group.getName());
    
                        // Could make sure the group still has the 
                        // expected filter...
                        _groupsRetained++;
                    }
                    else {
                        // new group!
                        group = new GroupDefinition();
                        group.setName(groupName);
                        group.setFactory(factory);
    
                        // !! look at what we're doing in refreshFactoryManager below,
                        // should we just do that transformation universally for all
                        // attributes we find to contain references?  There are none now,
                        // but it could happen someday...
                        Filter f = null;
                        if (multi) {
                            // this has to be a string
                            List<String> values = new ArrayList<String>();
                            if (obj != null)
                                values.add(obj.toString());
                            // !! what it null, will need a different kind of filter
                            f = ExternalAttributeFilterBuilder.buildOrFilter(ExternalAttributeFilterBuilder.IDENTITY_EXTERNAL,
                                                                             ExternalAttributeFilterBuilder.IDENTITY_JOIN,
                                                                             attname, values, "EQ");
                        }
                        else if (obj != null) {
                        	if (attname.equals(Identity.ATT_INACTIVE)) {
                        		f = Filter.eq(attname, Util.otob(obj));
                        	} else {
	                            //Need to check to see if the object is an identity...if it is
	                            //need to add the identity to the filter and not just the name.
	                            //Example: managers.
	                            if (obj instanceof Identity) {
	                                f = Filter.eq(attname, obj);
	                            } else if (obj != null && isMultiSystemAttribute(attname)) {
	                                // If this group is based off role assignments we need a multi-valued filter
	                                f = Filter.containsAll(attname + ".name", Arrays.asList(obj.toString()));
	                            } else if (obj != null){
                                    // !! jsl - this will suck since we don't know here whether or not the
                                    // attribute is indexed, if it is on Oracle then it's likely a _ci and 
                                    // the group filter won't use the index
	                                f = Filter.eq(attname, obj.toString());
	                            }
                        	}
                        }
                        else if (isMultiSystemAttribute(attname)) {
                            // If this group is based off role assignments we need a multi-valued filter
                            f = Filter.isempty(attname);
                            // also set this flag so the UI can reliably know
                            // without having to dig into the filter
                            group.setNullGroup(true);
                        }
                        else {
                            f = Filter.isnull(attname);
                            // also set this flag so the UI can reliably know
                            // without having to dig into the filter
                            group.setNullGroup(true);
                        }
                    
                        // Originally let the factory specify a filter as well
                        // as an attribute, the filter could be used to 
                        // constrain the set of values, sort of like an
                        // LDAP base context.
                        //if (groupFilter != null)
                        //f = Filter.and(f, groupFilter);
    
                        group.setFilter(f);
    
                        // !! how do we determine what we should
                        // do with this group?  Assume for now that
                        // factories always generate indexed groups.
                        group.setIndexed(true);
                        
                        // nice to see in the console
                        group.setDescription(factory.getName());
    
                        // use the factory's assigned scope
                        group.setAssignedScope(factory.getAssignedScope());
                        
                        _context.saveObject(group);
                        _context.commitTransaction();
                        _context.decache(group);
    
                        _groupsCreated++;
                    }
    
                    // make this optional?
                    // yes, we need to refresh GroupDefinitions in 
                    // a different loop to pick up the ones that don't
                    // have factories
                    //refreshGroup(group);
                }
                
                // bump the refresh date on the factory
                factory.setLastRefresh(new Date());
                _context.saveObject(factory);
                _context.commitTransaction();
            } 
            catch (GeneralException e) {
                Message msg = new Message(Message.Type.Error,
                            MessageKeys.ERR_GRP_FACT_REFRESH_ATTR, factory.getName(), attname);
                _messages.addMessage(msg);                
                log.error(msg, e);
            }
        }

        // anything remaining in the map is dormant
        // should provide an option to delete!
        if (groups != null) {
            Iterator<String> it = groups.keySet().iterator();
            while (it.hasNext() && !_terminate) {
                String name = it.next();

                if (_deleteDormantGroups) {
                    GroupDefinition group = getGroup(factory, name);
                    if (group != null) {
                        deleteGroup(group);
                        _groupsDeleted++;
                    }
                }
                else {
                    _groupsDormant++;
                    trace("Retaining dormant group '" + name + "'");
                }
            }
        }


        Meter.exit(81);
    }

    /*
     * This method returns true if the attribute in question is a system attribute that was
     * not flagged as multi-valued even though it actually is.
     */
    private boolean isMultiSystemAttribute(String attname) {
        if (Util.isNullOrEmpty(attname)) {
            return false;
        } else {
            return "bundles".equals(attname) || "assignedRoles".equals(attname);
        }
    }

    /**
     * Refresher for the global group including all identities.
     * This is used if we find a GroupFactory that has a null attribute.
     * Hmm, not sure this is such a good idea, it will be
     * expensive to index and its an easy mistake.
     */
    private void refreshFactoryGlobal(GroupFactory factory,
                                      Map<String,String> groups)
        throws GeneralException {

        trace("Generating groups for factory " + factory.getName());
        GroupDefinition group = getGroup(factory, factory.getName());

        if (group != null) {
            groups.remove(group.getName());
            _groupsRetained++;
        }
        else {
            // create a group with no filter
            group = new GroupDefinition();
            group.setName(factory.getName());
            group.setFactory(factory);

                // do with this group?  Assume for now that
                // factories always generate indexed groups.
            group.setIndexed(true);
                
            // don't need it but be consistent with factory groups
            group.setDescription(factory.getName());

            // use the factory's assigned scope
            group.setAssignedScope(factory.getAssignedScope());
                
            _context.saveObject(group);
            _context.commitTransaction();
            _context.decache(group);
            _groupsCreated++;
        }
    }

    /**
     * Refresher for the pseudo-attribute "bundles" which 
     * is a multi-valued reference.
     * 
     * There are two ways we could do this:
     * searching for the distinct values of bundles.name
     * from Identity.class or just getting the names of
     * all Bundle classes.  I doubt you can do bundles.name
     * with a Critiera query, we would have to use HQL.
     * It is much easier just to get all the Bundle names
     * but what will be different about this factory is that
     * groups may be defined with no identitites.
     */
    private void refreshFactoryBundles(GroupFactory factory,
                                       Map<String,String> groups)
        throws GeneralException {

        trace("Generating groups for factory " + factory.getName());

        List<String> props = new ArrayList<String>();
        props.add("name");
        QueryOptions ops = new QueryOptions();
        ops.setCloneResults(true);
        Iterator<Object[]> it = _context.search(Bundle.class, ops, props);

        while (it.hasNext() && !_terminate) {                

            String groupName = (String)(it.next()[0]);
            GroupDefinition group = getGroup(factory, groupName);
            if (group != null) {
                if (groups != null)
                    groups.remove(group.getName());
                _groupsRetained++;
            }
            else {
                // new group!
                group = new GroupDefinition();
                group.setName(groupName);
                group.setFactory(factory);

                // NOTE: Have to use dotted notation so we can compare
                // by name.
                List<String> values = new ArrayList<String>();
                values.add(groupName);
                Filter f = Filter.containsAll(Identity.ATT_BUNDLES + ".name", values);

                group.setFilter(f);
                group.setIndexed(true);
                group.setDescription(factory.getName());

                // use the factory's assigned scope
                group.setAssignedScope(factory.getAssignedScope());
                    
                _context.saveObject(group);
                _context.commitTransaction();
                _context.decache(group);

                _groupsCreated++;
            }
        }

        // also create a "null" group for users that have no business roles
        String groupName = "No " + factory.getName();
        GroupDefinition group = getGroup(factory, groupName);
        if (group != null) {
            if (groups != null)
                groups.remove(group.getName());
            _groupsRetained++;
        }
        else {
            group = new GroupDefinition();
            group.setName(groupName);
            group.setFactory(factory);

            // Sigh, note that we can't use isnull here because HQL
            // doesn't like a null comparison with a List, you have
            // to use the HQL "size(something) = 0".  This is available
            // in a Filter by using the ISEMPTY operator. 
            String attname = factory.getFactoryAttribute();
            group.setFilter(Filter.isempty(attname));

            group.setNullGroup(true);
            group.setIndexed(true);
            group.setDescription(factory.getName());

            // use the factory's assigned scope
            group.setAssignedScope(factory.getAssignedScope());
                
            _context.saveObject(group);
            _context.commitTransaction();
            _context.decache(group);

            _groupsCreated++;
        }
    }

    /**
     * Refresher for the attribute "manager".
     * Originally this was treated as a simple attribute but
     * it resulted in Identity objects being serialized inside
     * the Filter inside the GroupDefinition.  We catch this
     * as a special case and convert the attribute in the filter
     * to manager.name.
     */
    private void refreshFactoryManager(GroupFactory factory,
                                       Map<String,String> groups)
        throws GeneralException {

        trace("Generating groups for factory " + factory.getName());

        String attname = Identity.ATT_MANAGER + ".name";

        QueryOptions ops = new QueryOptions();
        ops.setDistinct(true);
        ops.setCloneResults(true);
        List<String> props = new ArrayList<String>();
        props.add(attname);
            
        Iterator<Object[]> it;
        try {
            it = _context.search(Identity.class, ops, props);
            
            if (!it.hasNext()) {
                _messages.addMessage(new Message(Message.Type.Warn,
                                                 MessageKeys.ERR_MISSING_GRP_ATTR, factory.getName(), attname));
            }
                
            while (it.hasNext() && !_terminate) {                
                String managerName = (String)(it.next()[0]);
                String groupName = managerName;
                if (groupName == null) {
                    // special group for feral unmanaged identities
                    groupName = "No " + factory.getName();
                }
    
                trace("Checking group definition: " + groupName);

                // locate the existing definition, do these one at a time
                // so we don't overload the cache
    
                GroupDefinition group = getGroup(factory, groupName);
    
                if (group != null) {
                    if (groups != null)
                        groups.remove(group.getName());
                    // Could make sure the group still has the 
                    // expected filter...
                    _groupsRetained++;
                }
                else {
                    group = new GroupDefinition();
                    group.setName(groupName);
                    group.setFactory(factory);
    
                    Filter f = null;
                    if (managerName != null)
                        f = Filter.eq(attname, managerName);
                    else {
                        //NULL check won't work for manager.name, need to 
                        //use manager itself.
                        f = Filter.isnull(Identity.ATT_MANAGER);
                        // also set this flag so the UI can reliably know
                        // without having to dig into the filter
                        group.setNullGroup(true);
                    }
    
                    group.setFilter(f);
                    group.setIndexed(true);
                    group.setDescription(factory.getName());
                    group.setAssignedScope(factory.getAssignedScope());
                        
                    _context.saveObject(group);
                    _context.commitTransaction();
                    _context.decache(group);
    
                    _groupsCreated++;
                }
            }
                
            // bump the refresh date on the factory
            factory.setLastRefresh(new Date());
            _context.saveObject(factory);
            _context.commitTransaction();
        } 
        catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error,
                                      MessageKeys.ERR_GRP_FACT_REFRESH_ATTR, factory.getName(), attname);
            _messages.addMessage(msg);                
            log.error(msg, e);
        }
    }

    /**
     * Locate a factory group by name.
     * Since we can't assume all GroupDefinitions have unique names,
     * we have to combine the name with the factory.
     */
    private GroupDefinition getGroup(GroupFactory factory, String name) 
        throws GeneralException {

        GroupDefinition group = null;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("factory", factory));
        ops.add(Filter.eq("name", name));
        
        List<GroupDefinition> result = _context.getObjects(GroupDefinition.class, ops);
        if (result != null && result.size() > 0) {
            if (result.size() > 1) {
                // In theory this could happen if we're not cleanup up properly. 
                if (log.isErrorEnabled())
                    log.error("Multiple group definitions found for " + name + 
                              " in factory " + factory.getName());
            }
            group = result.get(0);
        }

        return group;
    }


    /**
     * Helper for generate(GroupFactory), calculate a map with keys
     * for every GroupDefinition generated from a GroupFactory.
     * 
     */
    private Map<String,String> getCurrentGroups(GroupFactory factory)
        throws GeneralException {

        Map<String, String> map = new HashMap<String,String>();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("factory", factory));

        // just get the names so we don't overload the Hibernate cache
        List<String> atts = new ArrayList<String>();
        atts.add("name");
        Iterator<Object[]> it = _context.search(GroupDefinition.class, ops, atts);
        if (it != null) {
            // there could be quite a few of these to pay attention to terminate
            while (it.hasNext() && !_terminate) {
                String name = (String)(it.next()[0]);
                map.put(name, name);
            }
        }

        // if we catch terminate down here throw an exception so we
        // don't try to do something with a partial list
        if (_terminate)
            throw new GeneralException(new Message(MessageKeys.TASK_EXCEPTION_TERMINATED));


        return map;
    }
    
    public void deleteGroupFactory(GroupFactory groupFactory) throws GeneralException {
        if(groupFactory!=null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("factory", groupFactory));
            List<GroupDefinition> defs = _context.getObjects(GroupDefinition.class, ops);
            for(Iterator<GroupDefinition> defIter = defs.iterator(); defIter.hasNext();) {
                GroupDefinition def = defIter.next();
                deleteGroup(def);
            }
            _context.removeObject(groupFactory);
            _context.commitTransaction();

            // !! why is this done - jsl
            _context.decache();
        }
    }

    /**
     * Delete a GroupDefinition and its associated indexes.
     * Normally called only for dormant groups.
     *
     * Probably need something to clean up GroupIndex's that 
     * somehow got a null definition reference.
     */
    public void deleteGroup(GroupDefinition group) throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("definition", group));
        group.setIndex(null);
        _context.saveObject(group);

        // this utility method will keep the cache clean and trace
        getScoreKeeper().removeObjects(GroupIndex.class, ops, true);

        _context.removeObject(group);
        _context.commitTransaction();
    }

    /**
     * Run group owner rules and assign owners to group definition objects of the factory.
     * 
     * @param factory
     * @throws GeneralException
     */
    public void assignGroupOwners(GroupFactory factory) throws GeneralException {
        if (factory == null) {
            return;
        }
        
        Rule groupOwnerRule = factory.getGroupOwnerRule();
        
        if (groupOwnerRule == null) {
            return;
        }
        
        Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put("factory", factory);

        Map<String,String> groups = getCurrentGroups(factory);
        
        Iterator<String> it = groups.keySet().iterator();

        // Exit if no iterator or empty group
        if (it == null || (groups.size() == 0)) {
            return;
        }
        
        Identity owner = null;
        
        while (it.hasNext() && !_terminate) {
            String groupName = (String)it.next();
            GroupDefinition group = getGroup(factory, groupName);

            inputs.put("group", group);
            
            Object result = _context.runRule(groupOwnerRule, inputs);
            
            if (result instanceof Identity) {
                owner = _context.getObjectById(Identity.class, ((Identity)result).getId());
            }
            else if (result instanceof String) {
                owner = _context.getObjectByName(Identity.class, (String)result);
            }
            
            group.setOwner(owner);
            
            _context.saveObject(group);
            _context.commitTransaction();
            _context.decache(group);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // GroupDefinition Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Refresh all GroupDefinitions that have any actions.
     * Currently the only action we have is indexing.
     */
    public void refreshGroups() throws GeneralException {

        trace("Refreshing all group indexes");
        Meter.enter(90, "refreshGroups");

        _groupsIndexed = 0;
        _groupNames = new ArrayList<String>();

        // do the search by name and fetch them one at a time just in 
        // case there are a lot of them

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("indexed", new Boolean(true)));
        ops.setCloneResults(true);
        List<String> atts = new ArrayList<String>();
        atts.add("id");
        Iterator<Object[]> it = _context.search(GroupDefinition.class, 
                                                ops, atts);

        while (it.hasNext() && !_terminate) {
            String id = (String)(it.next()[0]);

            GroupDefinition group = _context.getObjectById(GroupDefinition.class, id);
            if (group != null) {

                // hmm, only include groups that don't have factories
                // since we'll be returning the names of the factories
                // in another list.  If we include the factory groups
                // the list could be long and confusing.
                if (group.getFactory() == null)
                    _groupNames.add(group.getName());

                refreshGroup(group);

                // Hibernate slows way down when the cache bloats, so clear
                // it after every group.  Scorekeeper should already be
                // clearing the cache after some number of identities
                // but it can't hurt to do it again here.  Don't bother
                // with cacheAge, just do it!
                _context.decache();
            }
        }

        Meter.exit(90);
        trace("Finished refreshing all group indexes");
    }

    /**
     * Refresh one group.  If the group is marked for indexing, we
     * refresh the index.
     */
    public void refreshGroup(GroupDefinition group) throws GeneralException {

        if (group.isIndexed()) {

            // Scorekeeper has enough trace
            //trace("Refreshing group " + group.getName());

            //Filter f = group.getFilter();
            //if (f != null)
            //trace(f.toString());

            // this will do the scores, violation counts and commit
            ScoreKeeper sk = getScoreKeeper();
            GroupIndex index = sk.refreshIndex(group);

            // In theory here is where we could add other interesting
            // group statistics that aren't processed by ScoreKeeper,
            // but try to keep the Identity iteration encapsulated
            // in ScoreKeeper for now.

            _groupsIndexed++;

            // accumulate this for testing
            _groupIndexesDeleted += sk.getGroupIndexesDeleted();
        }
    }

    /**
     * Remove all group indexes.
     * This is a debugging utility necessary because we can't
     * use the console to simply delete all GroupIndex objects
     * because there is a reference from the GroupDefinition to
     * the most recent GroupIndex and deleteding that one causes
     * a foreign key violation in Hibernate.  Need to think
     * about a better way to maintain this reference or
     * calculate it on the fly!!
     */
    public void deleteGroupIndexes() throws GeneralException {

        // do the search by name and fetch them one at a time just in 
        // case there are a lot of them

        ScoreKeeper sk = getScoreKeeper();
        QueryOptions ops = new QueryOptions();
        // get all of them, there may be lingering indexes
        //ops.add(Filter.eq("indexed", new Boolean(true)));
        Set<String> groupDefIds = _committer.getIds(ops);
        
        // Remove the indexes, decaching after every 100 groups
        BatchCommitter.BatchExecutor<GroupDefinition> executor = new BatchCommitter.BatchExecutor<GroupDefinition>() {
			@Override
			public void execute(SailPointContext context, GroupDefinition group, Map<String, Object> extraParams) throws GeneralException {
		        if (group != null) {
		            if (group.getIndex() != null) {
		                trace("Removing index for group " + group.getName());
		                group.setIndex(null);
		                _context.saveObject(group);
		                _context.commitTransaction();
		            }
		        }
			}
        };
        _committer.execute(groupDefIds, 100, executor, null);

        // now waste 'em
        trace("Removing all group indexes");

        // this will obey maxCacheAge and trace
        sk.removeObjects(GroupIndex.class, null, true);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // History Simulation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate interesting group index histories for testing and demos.
     */
    public void generateGroupHistory() throws GeneralException {

        // do the search by name and fetch them one at a time just in 
        // case there are a lot of them

        prepare();

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("indexed", new Boolean(true)));
        Set<String> groupIds = _committer.getIds(ops);
        // Generate history, decaching after every 100 groups
        // First we set up our executor
        final String SCORE_KEEPER = "scoreKeeper";
        BatchCommitter.BatchExecutor<GroupDefinition> executor = new BatchCommitter.BatchExecutor<GroupDefinition>() {
			@Override
			public void execute(SailPointContext context, GroupDefinition group, Map<String, Object> extraParams) throws GeneralException {
		        if (group != null) {
		        	if (group.isIndexed()) {
		        		ScoreKeeper sk = (ScoreKeeper) extraParams.get(SCORE_KEEPER);
		        		sk.generateHistory(group);
		        	}
		            if (group.getIndex() != null) {
		                trace("Removing index for group " + group.getName());
		                group.setIndex(null);
		                _context.saveObject(group);
		            }
		        }
			}
        };
        Map<String, Object> params = new HashMap<String, Object>();
        ScoreKeeper sk = getScoreKeeper();
        params.put(SCORE_KEEPER, sk);
        // The setup is done so now the BatchCommitter can generate histories without cluttering the Hibernate cache
        _committer.execute(groupIds, 100, executor, params);
    }

    public void generateHistory(GroupDefinition group) 
        throws GeneralException {

        prepare();
        getScoreKeeper().generateHistory(group);
    }

}

