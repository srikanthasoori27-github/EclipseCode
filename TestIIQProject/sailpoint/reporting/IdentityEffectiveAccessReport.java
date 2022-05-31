package sailpoint.reporting;

import net.sf.jasperreports.engine.JasperReport;
import sailpoint.api.AccountGroupService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskDefinition;
import sailpoint.reporting.datasource.IdentityEffectiveAccessDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

public class IdentityEffectiveAccessReport extends JasperExecutor {

    @Override
    protected TopLevelDataSource getDataSource() throws GeneralException {
        SailPointContext context = getContext();
        return new IdentityEffectiveAccessDataSource( identityReport.getDataSource(), context, new AccountGroupService( context ) );
    }

    @Override
    protected void init( TaskDefinition def, SailPointContext context,
            Attributes<String, Object> inputs ) {
        super.init( def, context, inputs );
        /* It would be nice if creating filters and what not were more composite 
         * but it is not so create a UserReport to build all the filters
         * and serve up identities */
        identityReport = new UserReport();
        identityReport.init( def, context, inputs );
    }
    @Override
    public void preFill( SailPointContext ctx, Attributes<String, Object> args,
            JasperReport report ) throws GeneralException {
        super.preFill( ctx, args, report );
        identityReport.preFill( ctx, args, report );
    }
    
    private UserReport identityReport;
}
