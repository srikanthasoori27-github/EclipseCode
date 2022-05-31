/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.lang.reflect.Constructor;
import java.util.List;

import sailpoint.connector.DefaultConnectorServices;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.AttributeDefinition;
import sailpoint.tools.GeneralException;

/**
 * The ActivityCollectorFactory factory to aid in getting 
 * collectors for a datasource.
 */
public class ActivityCollectorFactory {

    /**
     */
    public static ActivityCollector getCollector(ActivityDataSource ds) 
        throws GeneralException {

        if ( ds == null ) {
            throw new GeneralException("Missing required datasource value.");
        } 
        ActivityDataSource dataSource = (ActivityDataSource)ds.clone();
        
        String collectorClass = dataSource.getCollector();
        if ( collectorClass == null ) {
            throw new GeneralException("DataSource does not specify" +
                                       " a collector class");
        }
        return createCollector(collectorClass, dataSource);
    }
    
    public static List<AttributeDefinition> 
        getDefaultConfigAttributes(String collectorClassName) 
        throws GeneralException {

        // mock up a fake datasource
        ActivityDataSource mockDataSource = new ActivityDataSource();
        ActivityCollector collector = 
            createCollector(collectorClassName,mockDataSource);
        if ( collector == null ) {
            throw new GeneralException("Unable to create collector for ["
                                       + collectorClassName+"]");
        }
        return collector.getDefaultConfiguration();
    }

    /**
     *
     */
    private static ActivityCollector createCollector(String name,  
                                                     ActivityDataSource ds)
         throws GeneralException {

        Object instance = null;
        Class c = null;

        try {
            c = Class.forName(name);
        } catch (Exception e) {
            StringBuffer sb = new StringBuffer();
            sb.append("Couldn't load ActivityCollector class:");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }
        if ( c == null ) {
            StringBuffer sb = new StringBuffer();
            sb.append("Unknown error loading ActivityCollector class:");
            sb.append(name);
            throw new GeneralException(sb.toString());
        }

        try {
            Class dsClass = Class.forName("sailpoint.object.ActivityDataSource");
            Constructor constructor = c.getConstructor(dsClass);
            if ( constructor == null ) {
                throw new GeneralException("Failed to find a Constructor for class [" + name + "] which takes an ActivityDataSource.");
            }
            instance = constructor.newInstance(ds);
        } catch (Exception e) {
            throw new GeneralException("Failed to create ActivityCollector object:"
                    + e.toString());
        }

        if ( instance == null ) {
            throw new GeneralException("ActivityCollector instance was null.");
        }
        ActivityCollector activityCollector = (ActivityCollector)instance;
        activityCollector.setConnectorServices(new DefaultConnectorServices());
        return activityCollector;

    }
}
