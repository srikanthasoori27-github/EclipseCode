/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationContext;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * A certification builder that gets it's population from one or more group
 * definitions.
 */
public class GroupCertificationBuilder extends BaseIdentityCertificationBuilder {

    public static final String RESULT_IPOPS = "iPOPs";

    private static final Log log = LogFactory.getLog(GroupCertificationBuilder.class);

    //private Map<GroupDefinition,List<Identity>> groupOwnerMap;
    //private Map<GroupFactory,Rule> factoryRuleMap;
    private Map<String, Object> state = new HashMap<String, Object>();

    /**
     * Constructor.
     */
    public GroupCertificationBuilder(SailPointContext ctx, CertificationDefinition definition, EntitlementCorrelator correlator) {
        super(ctx, definition, correlator);
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContext(Certification)
     */
    public CertificationContext getContext(Certification cert) throws GeneralException {

        GroupDefinition group = context.getObjectById(GroupDefinition.class, cert.getGroupDefinitionId());
        if (null == group) {
            throw new GeneralException("Could not get group off certification: " + cert);
        }
        CertificationContext ctx = new GroupCertificationContext(this.context, definition, this.owners, group);
        return ctx;
    }

    private Map<GroupDefinition,List<Identity>> getGroupOwnerMap() throws GeneralException{
        List<CertificationDefinition.GroupBean> groups = definition.getGroupBeans(context);
        Map<GroupDefinition,List<Identity>> groupOwnerMap =
            new HashMap<GroupDefinition,List<Identity>>();

        if (null != groups) {
            for (CertificationDefinition.GroupBean group : groups) {
                groupOwnerMap.put(group.getGroupDefinition(), group.getCertifiers());
            }
        }
        return groupOwnerMap;
    }

    private Map<GroupFactory,Rule> getFactoryRuleMap() throws GeneralException{
        List<CertificationDefinition.FactoryBean> factories = definition.getFactoryBeans(context);
        Map<GroupFactory,Rule> factoryRuleMap = new HashMap<GroupFactory,Rule>();
        for (CertificationDefinition.FactoryBean fb : factories) {
            GroupFactory factory =
                context.getObjectById(GroupFactory.class, fb.getId());

            Rule rule = null;
            String ruleName = fb.getCertifierRuleName();
            if (ruleName != null)
                //Must be Name
                rule = context.getObjectByName(Rule.class, ruleName);
            if (rule == null) {
                // Should have caught this in the UI, here we could
                // substitute a default rule or default certifier?
                // For now ignore the cert.
                if (log.isErrorEnabled())
                    log.error("Ignoring certification for factory " + factory.getName() +
                              " - no certifier rule");
            }
            else {
                factoryRuleMap.put(factory, rule);
            }
        }

        return factoryRuleMap;
    }

    /* (non-Javadoc)
     * @see sailpoint.api.CertificationBuilder#getContexts()
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException {

        List<CertificationContext> ctxs = new ArrayList<CertificationContext>();

        Map<GroupDefinition,List<Identity>> groupOwnerMap = getGroupOwnerMap();
        if (null != groupOwnerMap) {
            for (Map.Entry<GroupDefinition, List<Identity>> entry : groupOwnerMap.entrySet()) {
                CertificationContext ctx = new GroupCertificationContext(context,  definition, entry.getValue(),
                        entry.getKey());
                ctxs.add(ctx);
                super.addResult(RESULT_IPOPS, entry.getKey().getName());
            }
        }

        Map<GroupFactory,Rule> factoryRuleMap = getFactoryRuleMap();
        if (factoryRuleMap != null) {
            for (Map.Entry<GroupFactory, Rule> entry : factoryRuleMap.entrySet()) {
                GroupFactory factory = entry.getKey();
                Rule rule = entry.getValue();

                // We must have a rule by now
                if (rule == null) {
                    if (log.isErrorEnabled())
                        log.error("Ignoring certification of factory " + factory.getName() + 
                                  " - no rule to determine certifiers");
                }
                else {
                    // get the current groups for this factory
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("factory", factory));
                    List<GroupDefinition> groups = context.getObjects(GroupDefinition.class, ops);
                    if (groups != null) { 
                        for (GroupDefinition group : groups) {
                            List<Identity> certifiers = getGroupCertifiers(factory, group, rule);
                            if (certifiers == null) {
                                if (log.isErrorEnabled())
                                    log.error("Ignoring certification of definition " + 
                                          group.getName() + " - rule returned no certifiers");
                            }
                            else {
                                CertificationContext ctx =
                                        new GroupCertificationContext(context, definition, certifiers, group);
                                ctxs.add(ctx);
                            }
                        }
                    }
                }
            }
        }

        // set common properties
        for(CertificationContext ctx : ctxs){
            ctx.setCertificationGroups(getCertificationGroups());
        }

        return ctxs.iterator();
    }

    private List<Identity> getGroupCertifiers(GroupFactory factory,   
                                              GroupDefinition group, 
                                              Rule rule)
        throws GeneralException {

        // this is all the contex we have, might be nice to get
        // some of th task schedule arguments in here?
        Map<String,Object> args = new HashMap<String,Object>();
        args.put("factory", factory);
        args.put("group", group);
        args.put("state", this.state);
        Object retval = context.runRule(rule, args);

        // Don't throw exceptions if the rule returns bad identities.
        // Just add warnings to the result.
        List<String> notFound = new ArrayList<String>();
        List<Identity> certifiers =
            ObjectUtil.getObjects(context, Identity.class, retval, false, false, notFound);

        for (String name : notFound) {
            super.addWarning(new Message(MessageKeys.ERR_IDENTITY_NOT_FOUND_FOR_GROUP, name, group.getName()));
        }

        return certifiers;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GroupCertificationContext implementation
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * CertificationContext for building a group certifications.
     */
    public class GroupCertificationContext
        extends BaseIdentityCertificationBuilder.BaseIdentityCertificationContext{

        private GroupDefinition group;


        /**
         * Constructor.
         *
         * @param context
         * @param group
         * @param owners The owners of the certification.  Defaults to the
         *                  specified manager if this is null.
         */
        public GroupCertificationContext(SailPointContext context, CertificationDefinition definition,
                                         List<Identity> owners, GroupDefinition group) {
            super(context, definition, owners);
            this.group = group;
        }

        @Override
        protected List<Identity> getOwnersInternal() {
            return null;
        }

        public Iterator<Identity> getPopulation() throws GeneralException {

            QueryOptions ops = new QueryOptions();
            if (null != this.group.getFilter()) {
                ops.add(this.group.getFilter());
            }
            ops.setOrderBy("name");
            ops.setDistinct(true);

            return new IncrementalIdentityIterator(context, ops, false);
        }

        public boolean inPopulation(AbstractCertifiableEntity entity)
            throws GeneralException {

            Filter f = Filter.and(this.group.getFilter(),
                                  Filter.eq("id", entity.getId()));
            QueryOptions qo = new QueryOptions();
            qo.add(f);
            int cnt = context.countObjects(Identity.class, qo);
            return (cnt > 0);
        }

        String generateDefaultName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_NAME_GENERIC,
                    new Message(getType().getMessageKey()), this.group.getName());
            return name.getLocalizedMessage();
        }

        String generateDefaultShortName() throws GeneralException {
            Message name = new Message(MessageKeys.CERT_SHORTNAME_GENERIC, group.getName());
            return name.getLocalizedMessage();
        }

        @Override
        protected void addNameParameters(CertificationNamer namer){
             namer.addParameter(CertificationNamer.NAME_TEMPLATE_GROUP_NAME, this.group.getName());
             if (null != this.group.getFactory()) {
                namer.addParameter(CertificationNamer.NAME_TEMPLATE_GROUP_FACTORY_NAME,
                        this.group.getFactory().getFactoryAttribute());
            }
        }

        @Override
        public void storeContext(Certification cert) throws GeneralException {
            super.storeContext(cert);
            cert.setGroupDefinition(this.group);
        }
    }
}
