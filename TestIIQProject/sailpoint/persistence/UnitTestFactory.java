/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * Utility to generate HibernatePersistenceManagers for the unit tests.
 *
 * A few of the unit tests we need a way to get a HibernatePersistenceManager,
 * but the one we normally use is created by Spring and wired deep under
 * the InternalContext via a ClassPersistenceManager.  Rather than bore
 * more holes in the PersistenceManager interface, we'll have Spring also
 * store Hibernate's SessionFactory in a static member of this class so
 * we can create more HibernatePersistenceManagers.  Only the unit tests
 * are allowed to access this.
 *
 * This is configured in hibernateBeans.xml
 * 
 * Author: Jeff
 * 
 */
package sailpoint.persistence;

import org.hibernate.SessionFactory;

import sailpoint.tools.GeneralException;

public class UnitTestFactory {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Our singleton.
     */
    private static UnitTestFactory _singleton;

    /**
     * The Hibernate session factory.
     */
    private SessionFactory _sessionFactory;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    static public UnitTestFactory getFactory() {
        if (_singleton == null) {
            synchronized (UnitTestFactory.class) {
                if (_singleton == null)
                    _singleton = new UnitTestFactory();
            }
        }
        return _singleton;
    }

    public UnitTestFactory() {
    }

    public void setSessionFactory(SessionFactory sf) {
        _sessionFactory = sf;
    }

    public SessionFactory getSessionFactory() {
        return _sessionFactory;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Factories
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Create a new HibernatePersistenceManager.
     */
    static public HibernatePersistenceManager getHibernatePersistenceManager() 
        throws GeneralException {
        
        UnitTestFactory factory = getFactory();
        SessionFactory sf = factory.getSessionFactory();
        if (sf == null)
            throw new GeneralException("No Hibernate SessionFactory!");

        HibernatePersistenceManager hpm = new HibernatePersistenceManager();

        // give it the breath of life
        hpm.setSessionFactory(sf);

        return hpm;
    }
}
