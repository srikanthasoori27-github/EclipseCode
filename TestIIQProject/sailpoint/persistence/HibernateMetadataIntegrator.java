/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 *
 *  Custom Integrator to obtain the Metadata. Metadata is used in sequence generation
 */

package sailpoint.persistence;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

public class HibernateMetadataIntegrator implements Integrator {

    public static HibernateMetadataIntegrator INSTANCE = new HibernateMetadataIntegrator();

    private Database _database;

    private Metadata _metadata;


    @Override
    public void integrate(Metadata metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        INSTANCE._metadata = metadata;
        INSTANCE._database = metadata.getDatabase();
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {

    }

    public Metadata getMetadata() { return this._metadata; }

    public Database getDatabase() { return this._database; }
}
