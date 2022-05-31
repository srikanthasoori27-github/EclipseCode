/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object providing methods to create IdentitySnapshot objects
 * from Identity objects.  Snapshots are used to remember
 * the state of an Identity at a given point in time.
 *
 * Meter range: 60-69
 * 
 * Author: Dan, Jeff
 *
 * jsl - Changed the way we manage the list of snapshots for an Identity, 
 * pulled some code over from mthe Aggregator.
 * 
 * CHANGE DETECTION AND SNAPSHOTS
 *
 * IdentitySnapshot objects are created to hold prior versions of
 * an Identity and its associated Links.  Since these are relatively large,
 * we only want to create a snapshot when we detect that a change was
 * made to an Identity during aggregation.  Further, there are system
 * parameters that determine the granularity of the snapshots.  We may
 * for example only be interested in monthly snapshots.  Changes to an
 * identity during a one month period can all be rolled into the
 * same snapshot.
 * 
 * There are two ways we can manage snapshots, let's call them
 * Leading Edge and Trailing Edge snapshots.  For these examples
 * we'll call the configured time between snapshots as the "window".
 *
 * Way 1: Leading Edge
 *
 * Here, a snapshot is created or modified whenever we detect changes to
 * an identity during Aggregation.  If the previous snapshot is older
 * than the configured snapshot window we create a new one.  If we have
 * an existing snapshot still within the window, we replace it with
 * a new one.  In effect, the snapshot "follows" the Identity through
 * the window until it reaches the end of the window, then a new 
 * snapshot is created in the next window.
 *
 * The snapshot will reflect the state of the user toward the end
 * the window (more recent in time).  The snapshot will always be
 * different than the Identity because one is not generated unless
 * the identity changes.
 * 
 * Way 2: Trailing Edge
 *
 * Here, a snapshot is created whenever the Identity after aggregation
 * is different from the previous snapshot and the previous snapshot
 * is older than the current window.  
 *
 * The snapshot will reflect the state of the user toward the
 * start of the window (farther back in time).  Once created, the
 * snapshot may be the same as the current Identity until another
 * change is detected within the window.
 *
 * To implement leading edge snapshots, we have to be prepared to 
 * create a new IdentitySnapshot with content taken from the Identity
 * *before* the aggregation refresh.  The easiest way to do that is to 
 * unconditionally make a snapshot at the beginning of each refresh,
 * then during refresh set a flag if any changes were detected.  After
 * refresh if changes were detected, compare the snapshot we made up front
 * with the previous snapshot.
 *
 * To implement trailing edge snapshots, we first do the aggregation
 * refresh, then create a snapshot from the current contents, then
 * compare the snapshot with the previous snapshot.
 *
 * Either way it is difficult to do the change detection without
 * building (and in the usual case throwing away) an IdentitySnapshot
 * for each identity.  Change detection requires almost the same amount
 * of logic as actually performing the refresh, it is error prone
 * to seperate them.  
 *
 * We're doing trailing edge snapshots because they're easier.
 * 
 * SNAPSHOT WINDOWS
 *
 * There are several ways we could manage windows.  Two that come
 * to mind are: Fixed and Relative.
 *
 * With Fixed Windows you pick a base time and a duration.  Examples:
 * first of every month, every 30 days from January 1, every third tuesday.
 * There will only be one snapshot created during that window.  If
 * a snapshot already exists in the window it will preserved 
 * (trailing edge) or replaced (leading edge).  
 *
 * With Relative windows you pick a duration.  Once a snapshot
 * is created it will "live" for this duration.  After this duration
 * a new snapshot is created.  You can also think of it as an expiration time.
 * The windows in effect are relative to the creation date of each
 * new snapshot, and will overlap randomly with the windows for other 
 * identities.  
 *
 * Relative windows are easier, so that's what we start with.
 * Fixed windows are possible but require a more complex configuration
 * model and UI.
 * 
 * 
 */
package sailpoint.api;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleSnapshot;
import sailpoint.object.Configuration;
import sailpoint.object.Difference;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.PermissionDifference;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleAssignmentSnapshot;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IdentityArchiver {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private SailPointContext _context;

    /**
     * Allow an entitlement correlator to be set by the caller so we can
     * share the cache of flattened business roles.
     */
    private EntitlementCorrelator _correlator;

    /**
     * The number of seconds it takes an identity snapshot to expire.
     * Extracted from the global configuration.
     * 0 = always exire
     * -1 = never expire
     */
    int _expirationSeconds;
    
    /**
     * Option to include a summary of the differences between 
     * the last snapshot in the new snapshot.
     * This is currently always disabled, and we'll calculate a list
     * of the current business roles instead.  But leave it around
     * for later, since this has been requested.
     */
    boolean _includeDifferences;

    /**
     * Set after we've cached our runtime state from the persistent store.
     */
    boolean _prepared;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityArchiver(SailPointContext ctx) {
        _context = ctx;
        _correlator = new EntitlementCorrelator(ctx);
    }

    public IdentityArchiver(SailPointContext ctx, EntitlementCorrelator ec) {
        _context = ctx;
        // allow this to be null!
        _correlator = ec;
    }

    public void setIncludeDifferences(boolean b) {
        _includeDifferences = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Control
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by the owning component to prepare us for use.  
     * Warm any caches we may want.
     */
    public void prepare() throws GeneralException {

        if (!_prepared) {
            
            _expirationSeconds = -1;
            Configuration config = _context.getConfiguration();
            if (config != null) { 
                String interval = config.getString(Configuration.IDENTITY_SNAPSHOT_INTERVAL);
                if (interval != null)
                    _expirationSeconds = Util.atoi(interval);
            }

            // This will usually be prepared already but in the case
            // where we make our own it won't be.  I don't think this
            // can happen any more - jsl
            if (_correlator != null)
                _correlator.prepare();

            _prepared = true;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Snapshot Management
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate an IdentitySnapshot if the Identity is different than the
     * previous snapshot and we're beyond the last snapshot's expiration date.
     *
     * This is "trailing edge snapshots" as described in the file comments.
     */
    IdentitySnapshot snapshot(Identity identity) throws GeneralException {

        prepare();

        // locate the most recent snapshot
        Meter.enter(60, "Find snapshot");
        // columns are id, created
        String snapId = null;
        Date created = null;
        Object[] info = ObjectUtil.getRecentSnapshotInfo(_context, identity);
        if (info != null) {
            snapId = (String)info[0];
            created = (Date)info[1];
        }
        Meter.exit(60);
        IdentitySnapshot newSnap = null;

        // with trailing edge, we don't replace until expired
        if (created == null || isExpired(created)) {

            // generate a new snapshot
            Meter.enter(61, "Create snapshot");
            newSnap = createSnapshot(identity);
            Meter.exit(61);

            // even though it may be expired, don't generate
            // a new shapshot unless changes were made
            if (snapId != null) {
                IdentitySnapshot snap = _context.getObjectById(IdentitySnapshot.class, snapId);
                if (snap != null) {
                    Meter.enter(62, "Diff snapshot");
                    Differencer diff = new Differencer(_context);
                    //println("****************Diffing:\n");
                    //println(snap.toXml());
                    //println(newSnap.toXml());

                    // NOTE: There has been some talk about displaying summary
                    // statistics about what changed between two snapshots,
                    // such as the number of attribte changes, number of
                    // entitlement changes, etc.  We can do that if we 
                    // call diff.diff rather than diff.equal, and sift through
                    // the returned IdentityDifference object to pull out
                    // some statistics.  But this is more expensive than
                    // diff.equal which can just stop after the first
                    // difference is detected, so I'm not sure if we want
                    // that all the time.  

                    if (_includeDifferences ) {

                        IdentityDifference diffs = diff.diff(snap, newSnap, null, true);
                        if (diffs != null && diffs.hasDifferences())
                            newSnap.setDifferencesAndTruncate(summarizeDiffs(diffs));
                        else
                            newSnap = null;
                    }
                    else if (diff.equal(snap, newSnap))
                        newSnap = null;

                    // don't let these accumulate in the cache!
                    _context.decache(snap);
                    Meter.exit(62);
                }
            }
        }

        if (newSnap != null) {

            // cascade will handle the creation, just reference it
            _context.saveObject(newSnap);
            
            // While creating a snapshot, IdentityArchiver will have
            // internally used EntitlementCorrelator to refresh entitlements,
            // but these were not stored in the Identity.  If we decide
            // to save the snapshot, should also refresh the Identity.
            // For aggregation this is ok, but it would probably be better
            // if we compared the bundle and exception list to see if there
            // were actually any changes before modifying the Identity?

            // Sigh, interface is somewhat twisted, but EntitlementCorrelator
            // will usually  be caching the state of this correlations.
            // Call this to make sure.

            // UPDATE: This now optional in 3.2 
            
            // UPDATE: The problem with calling saveAnalysis here is that it's possible to change 
            // the Identity's assigned roles by just having "Maintain Identity Histories" checked.
            // saveAnalysis calls saveAssignmentAnalysis, which is quick and dirty and causes issues.
            // Note also that saveAssignmentAnalysis has not been used since 6.3 in Identitizer.refresh.
            // The current Identitizer.refresh processing for reconciling assigned roles uses the Provisioner
            // and launching workflows.  We should leave all the heavy work for reconciling assigned roles
            // for when  it is explicitly done (i.e. - _doEntitlementCorrelation is true).
            // Calling just saveDetectionAnalysis makes more sense here.  IIQETN-6151.
            if (_correlator != null) {
                _correlator.analyzeIdentity(identity);
                _correlator.saveDetectionAnalysis(identity);
            }
        }

        return newSnap;
    }

    /**
     * Optionally calculate a string summarizing the differences
     * between two snapshots.  This is unfortunate because to display
     * anything meaningful we have to use localized text.  We can
     * do localization down here, but we can't do on-the-fly localiation
     * in the UI tier if we need to support more than one language
     * dynamically.
     *
     * The alternative would be to store this in some kind of 
     * delimited format and let the UI render it later.
     */
    private String summarizeDiffs(IdentityDifference idDiffs) {

        StringBuffer b = new StringBuffer();

        List<Difference> diffs = idDiffs.getAttributeDifferences();
        if (diffs != null) {
            int count = 0;
            int bundleCount = 0;
            int violationCount = 0;
            for (Difference d : diffs) {
                // treat differences in the bundle attribute as
                // a special case
                if (Identity.ATT_BUNDLES.equals(d.getAttribute())) {
                    bundleCount = countDiffs(d);
                }
                if (IdentityDifference.ATT_POLICY_VIOLATIONS.equals(d.getAttribute())) {
                    violationCount = countDiffs(d);
                }
                else {
                    // don't worry about multi-value changes here
                    count++;
                }
            }
            
            if (bundleCount > 0) {
                if (b.length() > 0) b.append(", ");
                b.append(Util.itoa(bundleCount));
                b.append(" business roles");
            }

            if (violationCount > 0) {
                if (b.length() > 0) b.append(", ");
                b.append(Util.itoa(violationCount));
                b.append(" policy violations");
            }

            if (count > 0) {
                if (b.length() > 0) b.append(", ");
                b.append(Util.itoa(count));
                b.append(" attributes");
            }
        }

        List<PermissionDifference> pdiffs = idDiffs.getPermissionDifferences();
        if (pdiffs != null) {
            // don't bother counting individual right changes
            int pcount = pdiffs.size();
            if (pcount > 0) {
                if (b.length() > 0) b.append(", ");
                b.append(Util.itoa(pcount));
                b.append(" permissions");
            }
        }

        diffs = idDiffs.getLinkDifferences();
        if (diffs != null) {
            // we're only supposed to be diffing attributes that
            // are entitlement relevant (another bug) so here
            // we can assume they are all relevant
            int ecount = diffs.size();
            if (ecount > 0) {
                if (b.length() > 0) b.append(", ");
                b.append(Util.itoa(ecount));
                b.append(" entitlements");
            }
        }

        return (b.length() > 0) ? b.toString() : null;
    }

    /**
     * Count the added and removed values on the given difference.
     */
    private int countDiffs(Difference d) {
        
        int count = 0;

        List<String> added = d.getAddedValues();
        if (added != null)
            count += added.size();
        List<String> removed = d.getRemovedValues();
        if (removed != null)
            count += removed.size();

        return count;
    }

    /**
     * Check to see if an existing snapshot has expired.
     * In the "relative window" implementation we just add a single
     * configurable duration to the creation date of the snapshot.
     * If the result is before the current date, then the snapshot
     * has expired.
     */
    private boolean isExpired(Date created) throws GeneralException {

        boolean expired = false;

        if (_expirationSeconds == 0) {
            // special case, always generate a new one if diffs detected
            expired = true;
        }
        else if (_expirationSeconds > 0) {
            if (created == null) {
                // hmm, may not have made it into Hibernate, assume expired
                expired = true;
            }
            else {
                Date expiration = Util.incrementDateBySeconds(created, _expirationSeconds);
                Date now = new Date();
                expired = (now.compareTo(expiration) >= 0);
            }
        }
        return expired;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Snapshot Creation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given an identity, drill down and create "snapshots" of the
     * interesting parts. The snapshots are simmular to the 
     * original objects, but all references to other objects are    
     * are turned into names.   
     *
     * jsl - allow an existing EntitlementCorrelator to be passed in
     * so we can reuse the cache of flattened business roles.
     */
    public IdentitySnapshot createSnapshot(Identity identity)
        throws GeneralException {	

        if ( identity == null) {
            throw new GeneralException("Cannot snapshot a null identity"
                                       + " object");
        }

        Meter.enter(63, "IdentityArchiver: prepare");

        IdentitySnapshot snapshot = new IdentitySnapshot();
        snapshot.setIdentityId(identity.getId());
        snapshot.setIdentityName(identity.getName());
        
        // just copy the whole thing over, don't see anything
        // we would want to filter
        snapshot.setScorecard(identity.getScorecard());

        // jsl - store this in displayName (soon to become "description")
        // so we have a generic way to show it in the console
        // TODO - now using description, is this really the right place?
        snapshot.setDescription(identity.getName());

        // Create a copy of the attributes.  Don't use the original because we
        // will possibly modify this by scrubbing hidden attributes out.
        Attributes<String,Object> attrs = identity.getAttributes();
        attrs = (null != attrs) ? new Attributes<String,Object>(attrs)
                                : new Attributes<String,Object>();
        scrubHiddenAttributes(attrs);
            
        // jsl - organization has been downgraded to an ordinary
        // attribute for now
        //Organization org = identity.getOrganization();
        //if ( org != null )
        //attrs.put(Identity.ATT_ORGANIZATION, org.getName());

        Identity manager = identity.getManager();
        if (manager != null)
            attrs.put(Identity.ATT_MANAGER, manager.getName());

        snapshot.setAttributes(attrs);
        Meter.exit(63);

        // Note that we have to re-run correlation in order to get
        // an accurate mapping of Bundles to entitlements, can't reliably
        // use the current state of the Identity since the Bundle
        // definitions may have changed.  See bug 336 for some history.
        // UPDATE: yeah, but it's expensive.  Since we're now storing
        // entitlement mappings on the identity allow this to be 
        // optimized out.  
        if (_correlator == null) {
            // new way, use what was cached
            Map<Bundle, List<EntitlementGroup>> mapping = 
                identity.getRoleEntitlementMappings(_context);
            snapshotBundles(snapshot, mapping);

            List<EntitlementGroup> exceptions = identity.getExceptions();
            snapshotExceptions(snapshot, exceptions);
        }
        else {
            // old way, full analysis
            Meter.enter(64, "IdentityArchiver: analyze");
            _correlator.analyzeIdentity(identity);
            Meter.exit(64);

            Meter.enter(65, "IdentityArchiver: copy");
            // Ask the correlator for the entitlements to business role
            // mapping so we can decorate our BundleSnapshot(s)
            // with the entitlements which caused the correlation.  
            Map<Bundle, List<EntitlementGroup>> mapping = 
                _correlator.getEntitlementMappings();
            snapshotBundles(snapshot, mapping);

            // Note that we must use the exception list calculated by the
            // correlator not the one currently on the identity.
            //List<EntitlementGroup> exceptions = identity.getExceptions();
            List<EntitlementGroup> exceptions = 
                _correlator.getEntitlementExceptions();
            snapshotExceptions(snapshot, exceptions);
        }

        List<RoleAssignment> roleAssignments = identity.getRoleAssignments();
        snapshotAssignedRoles(snapshot, roleAssignments);

        List<Link> links = identity.getLinks(); 
        snapshotLinks(snapshot, links);

        // Snapshot the policy violations.
        List<PolicyViolation> violations =
            ObjectUtil.getPolicyViolations(_context, identity);
        snapshot.setViolations(violations);
        
        // Not worried about system attributes like preferences, capabilities, 
        // login/refresh dates, snapshots and certifications

        // Capturing rich differences is currently disabled, but just
        // so we have something to show in the Summary column of the UI
        // let's save the currently assigned business roles.  That feels
        // useful even if we decide to show differences.
        List<BundleSnapshot> bundles = snapshot.getBundles();
        if (bundles != null && bundles.size() > 0) {
            StringBuffer b = new StringBuffer();
            for (BundleSnapshot bs : bundles) {
                if (b.length() > 0) b.append(", ");
                b.append(bs.getName());
            }
            snapshot.setSummaryAndTruncate(b.toString());
        }
        Meter.exit(65);

        return snapshot;
    }

    /**
     * Remove any hidden attributes (as defined by Identity.HIDDEN_ATTRIBUTES
     * from the given attributes map.
     */
    private static void scrubHiddenAttributes(Attributes<String,Object> attrs) {
        if (null != attrs) {
            for (String attr : Identity.HIDDEN_ATTRIBUTES) {
                attrs.remove(attr);
            }
        }
    }
    
    /** 
     * Make a snapshot of the bundles.  We don't store the definition of the 
     * bundle, but instead we explode the entitlements which caused an identity
     * to correlate to a bundle. Since the entitlements are nested in a bundle
     * snapshot we can imply the business role mapping and resue the 
     * EntitlementSnapshot object.
     * 
     * @ignore
     * TODO: use RoleTargets in the BundleSnapshot to handle multiple detections
     */
    private void snapshotBundles(IdentitySnapshot snapshot, 
                                 Map<Bundle, List<EntitlementGroup>> mapping ) {
                                 
        if ( mapping != null  ) {
            Set<Bundle> bundles = mapping.keySet();
            List<BundleSnapshot> bundleSnapshots = new ArrayList<BundleSnapshot>();
            for ( Bundle bundle : bundles ) {
                String bundleName = bundle.getName();
                BundleSnapshot ss = new BundleSnapshot(bundleName);

                List<EntitlementGroup> entitlementGroups = mapping.get(bundle);
                snapshotEntitlementGroups(ss, entitlementGroups);

                bundleSnapshots.add(ss);
            }
            if (bundleSnapshots.size() > 0)
                snapshot.setBundles(bundleSnapshots);
        }
    }
    
    private void snapshotAssignedRoles(IdentitySnapshot snapshot, List<RoleAssignment> roleAssignments) {
        Map<String, RoleAssignmentSnapshot> assignmentSnapshots = new HashMap<String, RoleAssignmentSnapshot>();
        for (RoleAssignment roleAssignment : Util.safeIterable(roleAssignments)) {
            // Don't snapshot revocations
            if (roleAssignment.isNegative()) {
                continue;
            }
            RoleAssignmentSnapshot assignmentSnapshot = new RoleAssignmentSnapshot(roleAssignment);
            if (assignmentSnapshots.containsKey(assignmentSnapshot.getName())) {
                RoleAssignmentSnapshot existingSnapshot = assignmentSnapshots.get(assignmentSnapshot.getName());
                assignmentSnapshot.getTargets().addAll(existingSnapshot.getTargets());
            }
            assignmentSnapshots.put(assignmentSnapshot.getName(), assignmentSnapshot);
        }
        if (!Util.isEmpty(assignmentSnapshots)) {
            snapshot.setAssignedRoles(new ArrayList<RoleAssignmentSnapshot>(assignmentSnapshots.values()));
        }
    }

    private void snapshotEntitlementGroups(BundleSnapshot snapshot,
                                           List<EntitlementGroup> groups) {
        if ( groups != null )  {
            List<EntitlementSnapshot> entitlements = 
                new ArrayList<EntitlementSnapshot>();
            for ( EntitlementGroup group : groups ) {
                EntitlementSnapshot eSnapshot = group.convertToSnapshot();
                entitlements.add(eSnapshot);
            }
            if (entitlements.size() > 0)
                snapshot.setEntitlements(entitlements);
        } 
    } 

    private void snapshotExceptions(IdentitySnapshot snapshot,
                                    List<EntitlementGroup> exceptions ) {
        if ( exceptions != null) {
            List<EntitlementSnapshot> exceptionSnapshots = 
                new ArrayList<EntitlementSnapshot>();
            for (EntitlementGroup exception : exceptions) {
                EntitlementSnapshot eSnapshot = exception.convertToSnapshot();
                exceptionSnapshots.add(eSnapshot);
            }
            if (exceptionSnapshots.size() > 0)
                snapshot.setExceptions(exceptionSnapshots);
        }
    }

    private void snapshotLinks(IdentitySnapshot snapshot, 
                               List<Link> links) {
        if ( links != null ) {
            List<LinkSnapshot> linkSnapshots = new ArrayList<LinkSnapshot>();
            for ( Link link : links) {
                Application resource = link.getApplication();
                if ( resource != null ) {
                    LinkSnapshot lSnapshot = new LinkSnapshot(link);
                    linkSnapshots.add(lSnapshot);
                }
            }
            if (linkSnapshots.size() > 0) {
                snapshot.setLinks(linkSnapshots);
                snapshotResourceNames(snapshot, linkSnapshots);
            }
        }
    }

    private void snapshotResourceNames(IdentitySnapshot snapshot, 
                                       List<LinkSnapshot> links) {
        if ( links != null ) {
            List<String> names = new ArrayList<String>();
            for ( LinkSnapshot link : links) {
                String resourceName = link.getApplication();
                if ( !names.contains(resourceName) ) 
                    names.add(resourceName);
            }
            String flatList = null;
            if ((names != null) && (names.size() > 0)) {
                flatList = Util.listToCsv(names);
                snapshot.setApplicationsAndTruncate(flatList);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Old Snapshot Management
    //
    // jsl - this was the original code for managing the generation
    // of snapshots based on a StorageMode enumeration.  We don't use
    // this any more, but I'm keeping it around for awhile.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Enumeration to define the frequency we should persist snapshots,
     * into the database.
     */
    // jsl - was public
    public enum StorageMode {
        ALWAYS,  
        DAILY, 
        WEEKLY,
        MONTHLY 
    }

    /**
     * Write a snapshot for the given identity to the database. This
     * method will create a snapshot for the given identity and
     * depending on the StroageMode specified it will remove any
     * snapshots.
     * <p>
     * <ul>
     *   <li>always - always add snapshot no checking is done</li>
     *   <li>daily - once a day, if we have more than one change per day
     *   can replace the last one with the more recent one</li>
     *   <li>weekly - once every seven days, if we have more then one change
     *    per week, we add the new snapshot and remove any from the previous
     *    week.</li>
     *   <li>monthly - once every 30 days, if we have more then one change
     *    per month, we add the new snapshot and remove any from the previous
     *    30 days.</li>
     * </ul>
     * <p>
     * NOTE: Its up to the caller to commitTransaction for
     * each of the snapshots in order for them to be
     * physcially written to the database.
     */ 
    //TODO: think about about multiple callers with different settings?
    //i.e. one monthly one daily? could one setting delete wanted
    //records? This is a configuration problem., come back to this..
    // jsl - was public
    public IdentitySnapshot persistSnapshot(Identity identity, 
                                             StorageMode mode )
        throws GeneralException {


        if ( mode != StorageMode.ALWAYS ) {

            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("identityName",identity.getName()));
 
            Date now = new Date();
            Date startDate = null;
            switch(mode) {
                case DAILY:
                    startDate = rollToStartOfDay(now); 
                    options.add(Filter.ge("created", startDate));
                    options.add(Filter.le("created", now));
                    break;
                case WEEKLY:
                    startDate = decrementDate(now, mode); 
                    options.add(Filter.ge("created", startDate));
                    options.add(Filter.le("created", now));
	            break;
                case MONTHLY:
                    startDate = decrementDate(now, mode); 
                    options.add(Filter.ge("created", startDate));
                    options.add(Filter.le("created", now));
	            break;
            }

            List<IdentitySnapshot> snapshots = 
                _context.getObjects(IdentitySnapshot.class, options);

            if ( snapshots != null ) {
                for ( IdentitySnapshot ss : snapshots ) {
                    _context.removeObject(ss);    
                }
            }
        }

        IdentitySnapshot snapshot = createSnapshot(identity);
        _context.saveObject(snapshot);
 
        return snapshot;
    }
 
    /* 
     * Given a string increment or decrement a date object to reflect
     * the supplied timeframe.
     */
    private static Date decrementDate(Date date, StorageMode mode) {
        GregorianCalendar cal =
            (GregorianCalendar) GregorianCalendar.getInstance();
        cal.setTime(date);
        switch(mode) {
            case WEEKLY:
                // set date to the first of the week
                cal.set(Calendar.DAY_OF_WEEK,1);
	        break;
            case MONTHLY:
                // set date to the first of the month
                cal.set(Calendar.DAY_OF_MONTH,1);
	        break;
        }
        // set the time to midnight 
        resetToStartOfDay(cal);

        return cal.getTime();
    }

    /**
     * Reset the calenar to the start of the day, which is just after 
     * midnight.
     */
    private static Date resetToStartOfDay(GregorianCalendar cal) {
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.AM_PM, Calendar.AM);
        return cal.getTime();
    }

    /* 
     * Roll the date to the start of the day. 00:00:00:00
     */
    private static Date rollToStartOfDay(Date date) {
        GregorianCalendar cal =
            (GregorianCalendar) GregorianCalendar.getInstance();
        cal.setTime(date);
        resetToStartOfDay(cal);
        return cal.getTime();
    }
}
