/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.lang.reflect.Constructor;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.DefaultConnectorServices;
import sailpoint.object.TargetSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * The TargetCollectorFactory factory to aid in getting 
 * collectors for a datasource.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class TargetCollectorFactory {

    /**
     * Build a target collector object. The object returned here
     * will actually be a TargetCollectorProxy object that provides
     * transformation and redirection services around the real
     * target collector class.
     */
    public static TargetCollector getTargetCollector(TargetSource ts)
        throws GeneralException {

        if ( ts == null ) {
            throw new GeneralException("Missing required datasource value.");
        }
        SailPointContext context = SailPointFactory.getCurrentContext();
        TargetSource dataSource = (TargetSource) XMLObjectFactory.getInstance().clone(ts, context);
        
        String collectorClass = dataSource.getCollector();
        if ( collectorClass == null ) {
            throw new GeneralException("DataSource does not specify" +
                                       " a collector class");
        }

        // get target collector
        TargetCollector targetCollector = createTargetCollector(collectorClass, dataSource);

        TargetCollectorProxy proxy = null;
        if ( null != targetCollector ) {
            proxy = new TargetCollectorProxy(targetCollector);
        }

        return proxy;
    }
    
    /**
     * Method will create a new target collector instance
     */
    @SuppressWarnings("unchecked")
    private static TargetCollector createTargetCollector(String name, TargetSource ts)
        throws GeneralException {

        Class<TargetCollector> c = null;
        try {
            c = (Class<TargetCollector>)Class.forName(name);
        } catch (Exception e) {
            StringBuffer sb = new StringBuffer();
            sb.append("Couldn't load TargetCollector class:");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }
        if ( c == null ) {
            StringBuffer sb = new StringBuffer();
            sb.append("Unknown error loading TargetCollector class:");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }

        TargetCollector targetCollector = null;
        try {
            Class<TargetSource> targetSourceClass = (Class<TargetSource>)Class.forName("sailpoint.object.TargetSource");
            Constructor<TargetCollector> constructor = c.getConstructor(targetSourceClass);
            if ( constructor == null ) {
                throw new GeneralException("Failed to find a Constructor for class [" + name + "] which takes an TargetSource.");
            }
            targetCollector = (TargetCollector)constructor.newInstance(ts);
            targetCollector.setConnectorServices(new DefaultConnectorServices());
        } catch (Exception e) {
            throw new GeneralException("Failed to create TargetCollector object:"
                    + e.toString(), e);
        }

        if ( targetCollector == null ) {
            throw new GeneralException("TargetCollector instance was null.");
        }
        return targetCollector;
    }
}
