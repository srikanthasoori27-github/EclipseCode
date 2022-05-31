package sailpoint.api;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class is iterates over a Collection of SailPointObjects, performing a 
 * unit of work that is defined in a BatchCommiter.BatchExecutor class on each
 * object.  After processing the number of items specified by the batchSize
 * parameter the transaction is committed and the context is optionally decached.
 *  
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class BatchCommitter<E extends SailPointObject> {
    private static final Log log = LogFactory.getLog(BatchCommitter.class);
    
    public interface BatchExecutor<E extends SailPointObject> {
        void execute(SailPointContext context, E obj, Map<String, Object> extraParams) throws GeneralException;
    }
    
    private SailPointContext context;
    private Class<E> classScope;
    private boolean disableDecache;
    private boolean terminate;
    
    /**
     * Instantiate a BatchCommitter that will process objects of the specified
     * class in batches, decaching after each batch
     * @param spClass Class of the set of SailPointObjects being committed
     * @param context SailPointContext in which the transactions take place
     */
    public BatchCommitter(Class<E> spClass, SailPointContext context) {
        this(spClass, context, false);
    }

    /**
     * Instantiate a BatchCommitter that will process objects of the specified
     * class in batches with the option to disable decaching.  This option is
     * provided in order to provide support for legacy logic that does not safely
     * manage persistent Hibernate POJOs
     * @param spClass Class of the set of SailPointObjects being committed
     * @param context SailPointContext in which the transactions take place
     * @param disableDecache true to disable decaching the context after every commit; 
     *                       false to avoid decaching
     */
    public BatchCommitter(Class<E> spClass, SailPointContext context, boolean disableDecache) {
        this.context = context;
        this.classScope = spClass;
        this.disableDecache = disableDecache;
    }

    public void setTerminate(boolean terminate) {
    	this.terminate = terminate;
    }
    
    /**
     * Use the specified batch executor to process the items specified by the Collection of IDs.
     * Note that this will commit and decache after every batch.
     * @param ids Collection<String> containing the IDs of the items to be processed
     * @param batchSize Size of the batch in which to process the items
     * @param executor BatchExecutor containing the logic require to process the specified items.  
     *                 The transaction will be committed after each batch and the Session will 
     *                 be cleared so plan accordingly in the executor
     */
    public void execute(Collection<String> ids, int batchSize, BatchExecutor<E> executor, Map<String, Object> extraParams) throws GeneralException {
        if (!Util.isEmpty(ids)) {
            Iterator<E> objectIterator = new IncrementalObjectIterator<E>(context, classScope, ids);
            if (!objectIterator.hasNext()) {
                // Abort now if there's nothing to do
                return;
            }
            
            int currentPosition = 0;
            int endOfBatch = -1;
            
            while (objectIterator.hasNext() && !terminate) {
                endOfBatch = Math.min(currentPosition + batchSize, ids.size());

                E item = objectIterator.next();
                if (item != null) {
                    executor.execute(context, item, extraParams);
                }

                currentPosition++;
                
                // Commit and decache the current batch to prevent bloat on the Hibernate session
                if (currentPosition % batchSize == 0) {
                    commitAndDecache(currentPosition, endOfBatch);
                }
            }
            
            // Commit and decache any left overs
            if (currentPosition % batchSize > 0 || terminate) {
                commitAndDecache(currentPosition, endOfBatch);
            }
        }
    }
    
    /**
     * @return The Set of all IDs of SailPointObjects of the type corresponding 
     * to this BatchCommitter's classScope that match the specified QueryOptions.
     * Returns all ids if the QueryOptions is null
     * @throws GeneralException when a database failure occurs
     */
    public Set<String> getIds(QueryOptions ops) throws GeneralException {
    	if (ops == null) {
    		ops = new QueryOptions();
    	}
        Iterator<Object[]> it = context.search(classScope, ops , "id");
        Set<String> ids = new HashSet<String>();
        while (it.hasNext() && !terminate) {
            String id = (String)(it.next()[0]);
            ids.add(id);
        }
        if (terminate) {
        	Util.flushIterator(it);
        }
        return ids;
    }
    
    private void commitAndDecache(final int currentPosition, final int endOfBatch) throws GeneralException {
        log.debug("BatchUtil committing the batch starting at position " + currentPosition + " and ending at position " + endOfBatch);
        context.commitTransaction();
        if (!disableDecache) {
            context.decache();            
        }
    }
}
