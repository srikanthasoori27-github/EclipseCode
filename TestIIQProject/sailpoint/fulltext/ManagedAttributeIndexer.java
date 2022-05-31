/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An extension of AbstractIndexer to index ManagedAttributes.
 *
 * Author: Jeff
 *
 * This was designed for LCM and is no longer used directly, instead
 * BundleManagedAttributeIndexer calls this to create a combined index.
 * It is not suitable for the Entitlement Catalog or analytics pages which
 * need to have the properties included derived from the SearchInputDefnitions.
 * See EntitlementCatalogIndexer.
 * 
 * Old Comments:
 * 
 * This could work one of two ways, we could try to store everything
 * required to render the MA grids in the index or we can just
 * store the MA id.  If we only store the id the index is somewhat
 * smaller but the UI that queries the index needs to fetch the MA
 * for each full text result row to build the result.
 * 
 * I'm going with the second approach first, let's see how big the
 * indexes get.  To make it easier to switch between fulltext and
 * HQL query results, use the same names as the HQL properties
 * expected by the sailpoint.web.lcm.EntitlementsRequestBean_search
 * UIConfig entry.
 *
 * The schema of the index result is:
 *
 *   id - database id of the ManagedAttribute
 *   application.id - id of the application
 *   applicatin.name - name of the application
 *   application.description - app description
 *   attribute - attribute name
 *   value - attribute value
 *   displayableName -  value or displayName
 *   owner.name - name of the owner
 *   owner.displayableName - name of the owner
 *   extended attributes - selected extended attributes, 
 *     default all
 * 
 * Of the above, displayableName and the extended attributes
 * are indexed.
 * 
 * In addition to the above "description" is
 * indexed but not stored because it is mangled and can't
 * be returned without a strict delimiting convention.
 * Caller will have to look up the appropriate description
 * from the Explanator cache.  
 * 
 */

package sailpoint.fulltext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.lucene.document.Document;

import sailpoint.api.Localizer;
import sailpoint.api.FullTextifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


public class ManagedAttributeIndexer extends AbstractIndexer {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The set of static fields that are allowed in the index.
     * These are combined with the extended attributes.
     *
     * UPDATE: We no longer use this though in theory an eventual
     * UI could use it to validate the field definitions.
     */
    static final String[] FIELDS = {
        FIELD_ID,
        FIELD_DESCRIPTION,
        FIELD_SCOPE,
        FIELD_DISPLAYABLE_NAME,
        FIELD_OWNER_ID,
        FIELD_OWNER_NAME,
        FIELD_OWNER_DISPLAY_NAME,
        "application.id",
        "application.name",
        "application.icon",
        "application.description",
        "application.owner.id",
        "application.assignedScope.path",
        "attribute",
        "value",
        "requestable",
        "type"
    };

    /**
     * The highest created or modified date seen during the last index refresh.
     */
    static Date LastIndexDate;

    /**
     * The number of objects indexed on the last refresh.
     */
    static int LastIndexCount;

    /**
     * A cache of the extended attributes defined for Application.
     */
    List<ObjectAttribute> _appExtended;

    /**
     * Running total of the number of objects indexed.
     */
    int _total;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //
    //////////////////////////////////////////////////////////////////////

    public ManagedAttributeIndexer() {
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
        return ManagedAttribute.class;
    }

    /**
     * Return true if the index needs to be refreshed because
     * of changes in the ManagedAttribute table.  Unfortunately due to 
     * hierarchy, it is difficult to incrementally update the index.  For each
     * modified MA we could check to see if the Schema even has a hierarchyAttribute
     * and if not just refresh that MA.  But if there is hierarchy there isn't
     * an easy way to figure out what the indiriect impact was.
     * 
     * Since ManagedAttributeIndexer can be subclassed or wrapped, this
     * must be implemented by the subclass or wrapper so the
     * static state doesn't conflict.
     */
    public boolean isIndexStale() throws GeneralException {
        throw new UnsupportedOperationException();
    }

    /**
     * Index one ManagedAttribute.
     */
    public Document index(SailPointObject obj) 
        throws GeneralException, IOException {

        Document doc = new Document();
        ManagedAttribute ma = (ManagedAttribute)obj;

        // We allow ManagedAttributes with a null value, but the index
        // doesn't so have to filter them.  Rethink whether we really
        // need to be keeping ManagedAttributes for null values.
        String value = ma.getValue();
        if (value == null) {
            if (log.isInfoEnabled()) {
                log.info("Skipping attribute with null value: " +
                         ma.getApplication().getName() + "/" +
                         ma.getAttribute());
            }
            return null;
        }

        if (log.isInfoEnabled()) {
            log.info("Indexing " + ma.getApplication().getName() + "/" +
                     ma.getAttribute() + "/" + ma.getValue());
        }
            
        addId(doc, ma.getId());

        Application app = ma.getApplication();
        addField(doc, "application.id", app.getId());
        addField(doc, "application.name", app.getName());
        addField(doc, "application.icon", app.getIcon());
        // !! what about localization of these, should we even bother with this?
        Localizer localizer = new Localizer(_builder.getSailPointContext());
        String appDescr = localizer.getDefaultLocalizedValue(app.getId(), Localizer.ATTR_DESCRIPTION);
        addField(doc, "application.description", appDescr);

        Identity appowner = app.getOwner();
        if (appowner != null)
            addField(doc, "application.owner.id", appowner.getId());
        // else need to store a *null*?

        Scope scope = app.getAssignedScope();
        addScope(doc, "application.assignedScope.path", scope);

        scope = ma.getAssignedScope();
        addScope(doc, FIELD_SCOPE, scope);

        addField(doc, "attribute", ma.getAttribute());
        addField(doc, "value", value);

        String dname = ma.getDisplayableName();
        addField(doc, FIELD_DISPLAYABLE_NAME, dname);

        Identity owner = ma.getOwner();
        if (owner != null) {
            addField(doc, FIELD_OWNER_ID, owner.getId());
            addField(doc, FIELD_OWNER_NAME, owner.getName());
            addField(doc, FIELD_OWNER_DISPLAY_NAME,
                     owner.getDisplayableName());
        }

        // !! It's nice to abbreviate these since we don't need 
        // the actual values but the same conversion has to happen 
        // on the Filter.
        addField(doc, "requestable", ma.isRequestable());

        String type = ma.getType();
        if (type == null) type = ManagedAttribute.Type.Entitlement.name();
        addField(doc, "type", type);

        addExtendedAttributes(doc, ma);

        // also allow the extended atts from the Application
        addApplicationExtended(doc, ma.getApplication());

        addDescriptions(doc, ma.getDescriptions());

        addTargets(doc, ma);

        if (_addObjectClass) {
            addField(doc, FullTextifier.FIELD_OBJECT_CLASS, ManagedAttribute.class.getSimpleName());
        }

        _total++;
        return doc;
    }

    /**
     * For MAs, we also add the extended attributes from the
     * application.
     */
    private void addApplicationExtended(Document doc, Application app) {

        if (_appExtended == null) {
            ObjectConfig config = ObjectConfig.getObjectConfig(Application.class);
            if (config != null)
                _appExtended = config.getExtendedAttributeList();
        }

        Map<String,Object> atts = app.getExtendedAttributes();
        if (_appExtended != null && atts != null) {
            for (ObjectAttribute att : _appExtended) {
                String name = att.getName();
                Object value = atts.get(name);
                if (value != null && (value instanceof String))
                    addField(doc, "application." + name, value);
            }
        }
    }

    /** 
     * Optionally add attribute and permission targets.
     */
    private void addTargets(Document doc, ManagedAttribute ma)
        throws GeneralException {

        if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {

            // leave out file shares, though we may want those someday
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("objectId", ma.getId()));
            // currently null means unstructured data from a collector (file share)
            ops.add(Filter.notnull("targetType"));

            // don't care about the type distinction yet, just merge them together
            //TODO: Should we make these properties configurable? -rap
            List<String> props = new ArrayList<String>();
            props.add("targetName");
            props.add("target.fullPath");

            //Don't need duplicates
            Set<String> targets = new HashSet<>();
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

        final String resultName = "attributesIndexed";

        int prev = result.getInt(resultName);
        result.setAttribute(resultName, Util.itoa(prev + _total));
    }

    @Override
    protected QueryOptions getQueryOptions() {
        Filter nonReqFilter = Filter.eq("requestable", new Boolean(true));
        QueryOptions options = new QueryOptions();
        options.add(nonReqFilter);

        return options;
    }
}

