/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import sailpoint.api.SailPointContext;
import sailpoint.classification.ClassificationFetcher;
import sailpoint.classification.ClassificationResult;
import sailpoint.fam.model.Application;
import sailpoint.fam.model.ResourceType;
import sailpoint.fam.service.FAMClassificationService;
import sailpoint.object.Configuration;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.fam.service.FAMWidgetService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import javax.ws.rs.core.Response;
import java.util.Iterator;

/**
 * Service class to aid in fetching data from File Access Manager
 */
public class FAMService implements ClassificationFetcher {

    private SailPointContext _ctx;
    FAMConnector _connector;
    FAMClassificationService _classificationService;
    FAMWidgetService _widgetService;

    public FAMService(SailPointContext ctx) {

        _ctx = ctx;
        _connector = new FAMConnector();
    }

    public FAMService(SailPointContext ctx, Configuration config) {

        _ctx = ctx;
        _connector = new FAMConnector(config);
    }

    @Override
    public Iterator<ClassificationResult> getClassifications(QueryOptions ops) throws GeneralException {

        FAMClassificationService classificationService = getClassificationService();
        return classificationService.getClassifications(ops);

    }

    @Override
    public Iterator<ClassificationResult> getClassifications(SailPointObject obj, QueryOptions ops)
        throws GeneralException {

        FAMClassificationService classificationService = getClassificationService();
        return classificationService.getClassifications(obj, ops);
    }

    /**
     * Check health of the File Access Manager Module. This uses the ResourceType SCIM API and checks the response
     * @throws GeneralException
     */
    public void checkHealth() throws GeneralException {
        Response response = null;
        try {
            try {
                response = _connector.getObjects(ResourceType.class, null, null);
                // drain it, to be safe
                response.readEntity(String.class);
            } catch (Exception e) {
                String msg = Message.localize("fam_test_connection_fail").getLocalizedMessage();
                throw new GeneralException(msg);
            }

            if (response.getStatus() != 200) {
                Object[] msgArgs = new Object [] {
                        response.getStatusInfo().getStatusCode(),
                        response.getStatusInfo().getReasonPhrase()
                };
                String msg = Message.localize("fam_test_connection_fail_with_status", msgArgs).getLocalizedMessage();
                throw new GeneralException(msg);
            }
        }
        finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Test connection to FAM. This differs from checkHealth, because the ResourceType SCIM endpoint does not require
     * authN
     * @throws GeneralException
     */
    public void testConnection() throws GeneralException {
        Response response = null;
        try {
            try {
                QueryOptions ops = new QueryOptions();
                ops.setResultLimit(1);
                response = _connector.getObjects(Application.class, ops, null);
                // drain it, to be safe
                response.readEntity(String.class);
            } catch (Exception e) {
                String msg = Message.localize("fam_test_connection_fail").getLocalizedMessage();
                throw new GeneralException(msg);
            }

            if (response.getStatus() != 200) {
                Object[] msgArgs = new Object [] {
                        response.getStatusInfo().getStatusCode(),
                        response.getStatusInfo().getReasonPhrase()
                };
                String msg = Message.localize("fam_test_connection_fail_with_status", msgArgs).getLocalizedMessage();
                throw new GeneralException(msg);
            }
        }
        finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public FAMClassificationService getClassificationService() {
        if (_classificationService == null) {
            _classificationService = new FAMClassificationService(_ctx, _connector);
        }

        return _classificationService;
    }

    public void setClassificationService(FAMClassificationService svc) {
        this._classificationService = svc;
    }


    public FAMWidgetService getWidgetService() {
        if (_widgetService == null) {
            _widgetService = new FAMWidgetService(_ctx, _connector);
        }
        return _widgetService;
    }

    //Help with unit tests.
    public void setConnector(FAMConnector connector) {
        this._connector = connector;
    }


}
