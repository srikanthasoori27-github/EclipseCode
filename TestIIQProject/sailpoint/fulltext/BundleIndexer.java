/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An extension of AbstractIndexer to index Bundle (roles).  
 *
 * Author: Jeff
 *
 * As of 7.0 this is no longer used directly, now BundleManagedAttributeIndexer
 * calls us to build a combined index.
 *
 */

package sailpoint.fulltext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.lucene.document.Document;

import sailpoint.api.Localizer;
import sailpoint.api.FullTextifier;
import sailpoint.api.SailPointContext;

import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleIndex;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


public class BundleIndexer extends AbstractIndexer {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The set of fields that will be in the index.
     * These are combined with the extended attributes.
     * Only these can be declared indexible in the FullTextIndex.
     *
     * UPDATE: We no longer use this though in theory an eventual
     * UI could use it to validate the field definitions.
     */
    final String[] Fields = {
        FIELD_ID,
        FIELD_NAME,
        FIELD_DISPLAYABLE_NAME,
        FIELD_DESCRIPTION,
        FIELD_SCOPE,
        FIELD_OWNER_ID,
        FIELD_OWNER_NAME,
        FIELD_OWNER_DISPLAY_NAME,
        "type",
        "defaultDescription",
        "riskScoreWeight",
        "disabled",
        "assignedScope.path",
        // new fields needed by Advanced Analytics
        "roleIndex.assignedCount",
        "roleIndex.detectedCount",
        "roleIndex.associatedToRole",
        "roleIndex.lastCertifiedMembership",
        "roleIndex.lastCertifiedComposition",
        "roleIndex.lastAssigned",
        "roleIndex.entitlementCount",
        "roleIndex.entitlementCountInheritance"
    };

    /**
     * Flag pulled out of the FullTextIndex indiciating that we should
     * include disabled roles in the index.
     */
    boolean _includeDisabled;
    
    /**
     * Running total of the roles actually added to the index.
     */
    int _total;

    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //
    //////////////////////////////////////////////////////////////////////

    public BundleIndexer() {
    }

    /**
     * This isn't called directly any more, so assume no.  You can't just
     * enable an index type without defining an appropriate config option
     * to enable it.
     */
    public boolean isSearchEnabled() {
        return false;
    }

    /**
     * Return the class we want to index.
     */
    public Class<? extends SailPointObject> getIndexClass() {
        return Bundle.class;
    }

    /**
     * Return true if roles have changed since the last refresh.
     * Because of role hierarchy it is very difficult to incrementally
     * refresh, have to redo the whole thing on any change.
     *
     * Since BundleIndexer can be subclassed or wrapped, this
     * must be implemented by the subclass or wrapper so the
     * static state doesn't conflict.
     */
    public boolean isIndexStale() throws GeneralException {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Cache the includeDisabled option before indexing.
     */
    @Override
    public void prepare(Builder builder) throws GeneralException {
        super.prepare(builder);
        
        _includeDisabled = _index.getBoolean(FullTextIndex.ATT_INCLUDE_DISABLED);
    }

    /**
     * Index a Bundle, exclude disabled bundles.
     */
    public Document index(SailPointObject obj) 
        throws GeneralException, IOException {
            
        Document doc = new Document();
        Bundle role = (Bundle)obj;

        if (!_includeDisabled && role.isDisabled()) {
            if (log.isInfoEnabled()) {
                log.info("Ignoring disabled role " + role.getName());
            }
            return null;
        }

        if (log.isInfoEnabled()) {
            log.info("Indexing " + role.getName());
        }

        addId(doc, role.getId());

        addField(doc, FIELD_NAME, role.getName());
        addField(doc, FIELD_DISPLAYABLE_NAME, role.getDisplayableName());
            
        // TODO: When we include the descriptions map in the Bundle object this will need to change.
        Localizer localizer = new Localizer(_builder.getSailPointContext(), role.getId());
        addDescriptions(doc, localizer.getAttributesMap(Localizer.ATTR_DESCRIPTION));

        Identity owner = role.getOwner();
        if (owner != null) {
            addField(doc, FIELD_OWNER_ID, owner.getId());
            addField(doc, FIELD_OWNER_NAME, owner.getName());
            addField(doc, FIELD_OWNER_DISPLAY_NAME, 
                     owner.getDisplayableName());
        }

        Scope scope = role.getAssignedScope();
        addScope(doc, FIELD_SCOPE, scope);

        String type = role.getType();
        if (type != null) {
            // TODO: this is the internal name, need to 
            // look up the RoleTypeDefinition, and store the
            // displayName insetad?
            addField(doc, "type", type);
        }

        // kludge: Until we figure out an upgrade strategy for
        // old Bundle objects that have a default description we
        // need to save it so we can have it in the results in case
        // the new 6.0 LocalizedAttribute table is missing.  
        String defaultRoleDescr = localizer.getDefaultLocalizedValue(role.getId(), Localizer.ATTR_DESCRIPTION);
        addField(doc, "defaultDescription", defaultRoleDescr);

        addField(doc, "riskScoreWeight", role.getRiskScoreWeight());

        // until we can upgrade Lucene to support IntField, these aren't useful
        //addRoleIndexFields(doc, role);

        addExtendedAttributes(doc, role);

        addTargets(doc, role);
            
        if (_addObjectClass) {
            addField(doc, FullTextifier.FIELD_OBJECT_CLASS, Bundle.class.getSimpleName());
        }

        _total++;

        return doc;
    }

    /** 
     * Optionally add attribute and permission targets.
     */
    private void addTargets(Document doc, Bundle role)
        throws GeneralException {

        if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("objectId", role.getId()));

            // don't care about the type distinction yet, just merge them together
            //TODO: Should we make these properties configurable? -rap
            List<String> props = new ArrayList<String>();
            props.add("targetName");
            props.add("target.fullPath");
                
            Set<String> targets = new HashSet<String>();
            SailPointContext spc = _builder.getSailPointContext();
            Iterator<Object[]> result = spc.search(TargetAssociation.class, ops, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                String target = (String)row[0];
                targets.add(target);
                String fullPath = Util.otos(row[1]);
                if (Util.isNotNullOrEmpty(fullPath)) {
                    targets.add(fullPath);
                }
            }

            addTargetFields(doc, targets);
        }
    }

    /**
     * Add fields from the RoleIndex associated with this role.
     */
    private void addRoleIndexFields(Document doc, Bundle role) throws GeneralException {

        RoleIndex rindex = role.getRoleIndex();
        if (rindex != null) {
            addField(doc, "roleIndex.assignedCount", rindex.getAssignedCount());
            addField(doc, "roleIndex.detectedCount", rindex.getDetectedCount());
            addField(doc, "roleIndex.associatedToRole", rindex.isAssociatedToRole());
            addField(doc, "roleIndex.lastCertifiedMembership", rindex.getLastCertifiedMembership());
            addField(doc, "roleIndex.lastCertifiedComposition", rindex.getLastCertifiedComposition());
            addField(doc, "roleIndex.lastAssigned", rindex.getLastAssigned());
            addField(doc, "roleIndex.entitlementCount", rindex.getEntitlementCount());
            addField(doc, "roleIndex.entitlementCountInheritance", rindex.getEntitlementCountInheritance());
        }
    }

    /**
     * Save state for isIndexStale.
     * Since this class may be subclassed or wrapped, this must be implemented by
     * the subclass or wrapper so the static state doesn't conflict.
     */
    public void saveRefreshState() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Add our results to the task result.
     * If we're refreshing several indexes at a time, accumulate the count
     * rather than overwritinging it.  This is odd, should have subsections
     * for each index definition.  Make it look like partitions?
     */
    public void saveResults(TaskResult result) {

        final String resultName = "rolesIndexed";

        int prev = result.getInt(resultName);
        result.setAttribute(resultName, Util.itoa(prev + _total));
    }

}

        
