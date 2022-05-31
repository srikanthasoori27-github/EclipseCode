/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.ArchivedCertificationItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class updates the statistics stored on a certification,
 * usually during a certification refresh.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class CertificationStatCounter {

    private SailPointContext context;

    public CertificationStatCounter(SailPointContext context) {
        this.context = context;
    }

    /**
     *
     * @param cert The certification to update
     * @param flush True if the certification a commit should be executed
     *  before the statistics are counted.
     * @throws GeneralException
     */
    public void updateCertificationStatistics(Certification cert, boolean flush)
            throws GeneralException {

        // Commit first so that all data is flushed to the database so the
        // queries will return the correct information.
        if (flush)
            context.commitTransaction();

        Meter.enter(190, "CertificationStatCounter: updateCertificationStatistics");

        List<CertStat> statistics = queryEntityStatistics(cert.getId());
        cert.getStatistics().reset();
        Certification.CertificationStatistics stats = cert.getStatistics();
        for(CertStat stat : statistics){
            stats.setTotalEntities(stats.getTotalEntities() + stat.getCount());

            // calculate entity status numbers
            if (stat.isInStatus(AbstractCertificationItem.Status.Complete,
                AbstractCertificationItem.Status.Challenged))
                stats.setCompletedEntities(stats.getCompletedEntities() + stat.getCount());
            if (stat.isInStatus(AbstractCertificationItem.Status.Delegated))
                stats.setDelegatedEntities(stats.getDelegatedEntities() + stat.getCount());

            // calculate entity continuous state numbers
            if (stat.isInState(AbstractCertificationItem.ContinuousState.Certified))
                stats.setCertifiedEntities(stats.getCertifiedEntities() + stat.getCount());
            if (stat.isInState(AbstractCertificationItem.ContinuousState.CertificationRequired))
                stats.setCertificationRequiredEntities(stats.getCertificationRequiredEntities() + stat.getCount());
            if (stat.isInState(AbstractCertificationItem.ContinuousState.Overdue))
                stats.setOverdueEntities(stats.getOverdueEntities() + stat.getCount());
        }

        //MEH Bug 16256, use counts and projection queries
        int archivedEntityCount = 0;
        int archivedCertItemCount = 0;
        QueryOptions ops = new QueryOptions(Filter.eq("parent.certification.id", cert.getId()));
        ops.setScopeResults(false);
        ops.setDistinct(true);
        List<String> projectionCols = new ArrayList<String>();
        projectionCols.add("count(id)");
        projectionCols.add("count(distinct parent.id)");
        Iterator<Object[]> archivedCertItemCountItr = context.search(ArchivedCertificationItem.class, ops, projectionCols);
        if(archivedCertItemCountItr.hasNext()){
        	Object[] result = archivedCertItemCountItr.next();
        	archivedCertItemCount = ((Long) result[0]).intValue();
        	archivedEntityCount = ((Long) result[1]).intValue();
        }

        cert.getStatistics().setExcludedEntities(archivedEntityCount);
        cert.getStatistics().setExcludedItems(archivedCertItemCount);

        List<CertStat> itemStatistics = queryItemStatistics(cert.getId());
        for(CertStat stat : itemStatistics){
            stats.setTotalItems(stats.getTotalItems() + stat.getCount());

            // calculate item status numbers
            if (stat.isInStatus(AbstractCertificationItem.Status.Complete,
                AbstractCertificationItem.Status.Challenged))
                stats.setCompletedItems(stats.getCompletedItems() + stat.getCount());
            if (stat.isInStatus(AbstractCertificationItem.Status.Delegated))
                stats.setDelegatedItems(stats.getDelegatedItems() + stat.getCount());

            // calculate entity continuous state numbers
            if (stat.isInState(AbstractCertificationItem.ContinuousState.Certified))
                stats.setCertifiedItems(stats.getCertifiedItems() + stat.getCount());
            if (stat.isInState(AbstractCertificationItem.ContinuousState.CertificationRequired))
                stats.setCertificationRequiredItems(stats.getCertificationRequiredItems() + stat.getCount());
            if (stat.isInState(AbstractCertificationItem.ContinuousState.Overdue))
                stats.setOverdueItems(stats.getOverdueItems() + stat.getCount());

            if (stat.isRemediationComplete())
                stats.setRemediationsCompleted(stats.getRemediationsCompleted() + stat.getCount());
            if (stat.isRemediationKickedOff())
                stats.setRemediationsKickedOff(stats.getRemediationsKickedOff() + stat.getCount());

            stats.incrementDecisionCount(cert.getType(), stat.getType(), stat.getDecision(), stat.getCount());
        }

        stats.savePercentComplete();

        Meter.exit(190);
    }

    /**
     * Update the remediationsKickedOff statistic on the given certification and
     * return this number.  THIS COMMITS THE TRANSACTION!
     *
     * @param cert The Certification for which to update the statistic.
     * @return The total number of kicked off remediations.
     */
    public int updateRemediationsKickedOff(Certification cert, boolean flush)
            throws GeneralException {

        if (flush)
            context.commitTransaction();

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.and(Filter.eq("parent.certification", cert),
                Filter.eq("action.remediationKickedOff", true)));
        int count = context.countObjects(CertificationItem.class, qo);
        cert.setRemediationsKickedOff(count);

        return count;
    }

    /**
     * Update the remediationsCompleted statistic on the given certification and
     * return this number.  THIS COMMITS THE TRANSACTION!
     *
     * @param cert The Certification for which to update the statistic.
     * @return The total number of completed remediations.
     */
    public int updateRemediationsCompleted(Certification cert, boolean flush)
            throws GeneralException {

        if (flush)
            context.commitTransaction();

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.and(Filter.eq("parent.certification", cert),
                Filter.eq("action.remediationCompleted", true)));
        int count = context.countObjects(CertificationItem.class, qo);
        cert.setRemediationsCompleted(count);

        return count;
    }

    public static int calculatePercentComplete(int completed, int total) {

        int percentComplete = 0;

        if (0 == total) {
            percentComplete = 100;
        } else {
            percentComplete = Util.getPercentage(completed, total );
        }

        return percentComplete;
    }

    /**
     * Count the number of items in the given certification that have the given
     * status and/or type.
     */
    private int countStatuses(Certification cert,
                              Class<? extends SailPointObject> clazz,
                              String certPath,
                              AbstractCertificationItem.Status... status)
            throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq(certPath, cert));

        if (null != status) {
            if (1 == status.length) {
                qo.add(Filter.eq("summaryStatus", status[0]));
            } else if (status.length > 1) {
                qo.add(Filter.in("summaryStatus", Arrays.asList(status)));
            }
        }

        return context.countObjects(clazz, qo);
    }

    private int countDecisions(Certification cert, CertificationItem.Type type,
                              CertificationAction.Status status)
            throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("parent.certification", cert));

        if (type != null){
            qo.add(Filter.eq("type", type));
        }

        if (null != status) {
            qo.add(Filter.eq("action.status", status));
        }

        return context.countObjects(CertificationItem.class, qo);
    }

    /**
     * Count the number of item in the given certification that have the given
     * continuous state.
     */
    private int countStates(Certification cert,
                            Class<? extends SailPointObject> clazz,
                            String certPath, AbstractCertificationItem.ContinuousState state)
            throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq(certPath, cert));
        if (null != state) {
            qo.add(Filter.eq("continuousState", state));
        }

        return context.countObjects(clazz, qo);
    }

    private List<CertStat> queryItemStatistics(String certId) throws GeneralException{

        List<CertStat> statistics = new ArrayList<CertStat>();

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("parent.certification.id", certId));
        qo.addGroupBy("type");
        qo.addGroupBy("action.status");
        qo.addGroupBy("summaryStatus");
        qo.addGroupBy("continuousState");
        qo.addGroupBy("action.remediationKickedOff");
        qo.addGroupBy("action.remediationCompleted");

        List<String> projectionCols = new ArrayList<String>();
        projectionCols.add("count(id)");
        projectionCols.add("type");
        projectionCols.add("action.status");
        projectionCols.add("summaryStatus");
        projectionCols.add("continuousState");
        projectionCols.add("action.remediationKickedOff");
        projectionCols.add("action.remediationCompleted");

        Iterator<Object[]> results = this.context.search(CertificationItem.class, qo, projectionCols);
        while(results.hasNext()){
            Object[] result = results.next();
            CertStat row = new CertStat();

            row.setCount(((Long)result[0]).intValue());
            row.setType((CertificationItem.Type)result[1]);
            row.setDecision((CertificationAction.Status)result[2]);
            row.setStatus((AbstractCertificationItem.Status)result[3]);
            row.setState((AbstractCertificationItem.ContinuousState)result[4]);
            row.setRemediationKickedOff((Boolean)result[5]);
            row.setRemediationComplete((Boolean)result[6]);

            statistics.add(row);
        }

        return statistics;
    }

    private List<CertStat> queryEntityStatistics(String certId) throws GeneralException{

        List<CertStat> statistics = new ArrayList<CertStat>();

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("certification.id", certId));
        qo.addGroupBy("summaryStatus");
        qo.addGroupBy("continuousState");

        List<String> projectionCols = new ArrayList<String>();
        projectionCols.add("count(id)");
        projectionCols.add("summaryStatus");
        projectionCols.add("continuousState");

        Iterator<Object[]> results = this.context.search(CertificationEntity.class, qo, projectionCols);
        while(results.hasNext()){
            Object[] result = results.next();
            CertStat row = new CertStat();

            row.setCount(((Long)result[0]).intValue());
            row.setStatus((AbstractCertificationItem.Status)result[1]);
            row.setState((AbstractCertificationItem.ContinuousState)result[2]);

            statistics.add(row);
        }

        return statistics;
    }

    private class CertStat{

        int count;
        CertificationItem.Type type;
        CertificationAction.Status decision;
        AbstractCertificationItem.Status status;
        AbstractCertificationItem.ContinuousState state;
        boolean remediationKickedOff;
        boolean remediationComplete;

        public boolean isInDecision(CertificationAction.Status... possibleDecisions){
            return inArray(possibleDecisions, this.decision);
        }

        public boolean isInState(AbstractCertificationItem.ContinuousState... possibleStates){
            return inArray(possibleStates, this.state);
        }

        public boolean isInStatus(AbstractCertificationItem.Status... possibleStatuses){
            return inArray(possibleStatuses, this.status);
        }

        private boolean inArray(Object[] arr, Object obj){
            if (arr != null && obj != null){
                for(Object o : arr){
                    if (obj.equals(o))
                        return true;
                }
            }

            return false;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public AbstractCertificationItem.Status getStatus() {
            return status;
        }

        public void setStatus(AbstractCertificationItem.Status status) {
            this.status = status;
        }

        public AbstractCertificationItem.ContinuousState getState() {
            return state;
        }

        public void setState(AbstractCertificationItem.ContinuousState state) {
            this.state = state;
        }

        public CertificationItem.Type getType() {
            return type;
        }

        public void setType(CertificationItem.Type type) {
            this.type = type;
        }

        public CertificationAction.Status getDecision() {
            return decision;
        }

        public void setDecision(CertificationAction.Status decision) {
            this.decision = decision;
        }

        public boolean isRemediationKickedOff() {
            return remediationKickedOff;
        }

        public void setRemediationKickedOff(Boolean remediationKickedOff) {
            this.remediationKickedOff = remediationKickedOff != null ? remediationKickedOff : false;
        }

        public boolean isRemediationComplete() {
            return remediationComplete;
        }

        public void setRemediationComplete(Boolean remediationComplete) {
            this.remediationComplete = remediationComplete != null ? remediationComplete : false;
        }
    }
}
